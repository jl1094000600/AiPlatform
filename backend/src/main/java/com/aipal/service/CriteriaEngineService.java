package com.aipal.service;

import com.aipal.dto.CriteriaConfigRequest;
import com.aipal.dto.EvaluationResult;
import com.aipal.entity.AiEvaluation;
import com.aipal.entity.AiEvaluationCriteria;
import com.aipal.entity.AiEvaluationSample;
import com.aipal.mapper.AiEvaluationCriteriaMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CriteriaEngineService {

    private static final Pattern COMPARISON = Pattern.compile(
            "^([a-zA-Z][a-zA-Z0-9_]*)\\s*(>=|<=|==|=|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final Pattern MULTIPLICATION = Pattern.compile(
            "^([a-zA-Z][a-zA-Z0-9_]*)\\s*\\*\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final List<String> DEFAULT_METRICS = List.of(
            "exact_match", "precision", "recall", "f1", "error_rate", "response_time_ms");

    private final AiEvaluationCriteriaMapper criteriaMapper;
    private final ObjectMapper objectMapper;

    public EvaluationResult evaluate(Long evaluationId, Object agentResponse) {
        Map<String, Double> metrics = numericMetrics(agentResponse);
        return buildResult("EVAL_" + evaluationId, loadCriteria(null), metrics);
    }

    public EvaluationResult evaluateSamples(AiEvaluation evaluation, List<AiEvaluationSample> samples) {
        if (samples == null || samples.isEmpty()) throw new IllegalArgumentException("Evaluation has no sample results");
        Map<String, Double> metrics = aggregateMetrics(samples);
        return buildResult(evaluation.getEvaluationCode(), loadCriteria(evaluation.getCriteriaConfig()), metrics);
    }

    public double calculateScore(AiEvaluationCriteria criteria, Object response) {
        ScoreOutcome outcome = scoreCriteria(criteria, numericMetrics(response));
        if (!outcome.success()) throw new IllegalArgumentException(outcome.details());
        return outcome.score();
    }

    private EvaluationResult buildResult(String evaluationCode, List<AiEvaluationCriteria> criteriaList,
                                         Map<String, Double> metrics) {
        EvaluationResult result = new EvaluationResult();
        result.setEvaluationCode(evaluationCode);
        result.setCriteriaScores(new ArrayList<>());

        double weightedScore = 0;
        double successfulWeight = 0;
        for (AiEvaluationCriteria criteria : criteriaList) {
            ScoreOutcome outcome = scoreCriteria(criteria, metrics);
            double weight = criteria.getWeight() == null ? 1.0 : criteria.getWeight();
            EvaluationResult.CriteriaScore item = new EvaluationResult.CriteriaScore();
            item.setCriteriaCode(criteria.getCriteriaCode());
            item.setCriteriaName(criteria.getCriteriaName());
            item.setWeight(weight);
            item.setDetails(outcome.details());
            if (outcome.success()) {
                item.setScore(outcome.score());
                weightedScore += outcome.score() * weight;
                successfulWeight += weight;
            }
            result.getCriteriaScores().add(item);
        }
        if (successfulWeight == 0) throw new IllegalStateException("No evaluation criteria could be calculated");
        result.setTotalScore(round(weightedScore / successfulWeight));
        return result;
    }

    private List<AiEvaluationCriteria> loadCriteria(String criteriaConfig) {
        QueryWrapper<AiEvaluationCriteria> wrapper = new QueryWrapper<AiEvaluationCriteria>().eq("status", 1);
        if (criteriaConfig != null && !criteriaConfig.isBlank()) {
            List<String> codes = List.of(criteriaConfig.split(",")).stream().map(String::trim)
                    .filter(value -> !value.isBlank()).toList();
            if (!codes.isEmpty()) wrapper.in("criteria_code", codes);
        }
        List<AiEvaluationCriteria> criteria = criteriaMapper.selectList(wrapper);
        if (!criteria.isEmpty()) return criteria;
        return DEFAULT_METRICS.stream().map(this::defaultCriteria).toList();
    }

    private AiEvaluationCriteria defaultCriteria(String metric) {
        AiEvaluationCriteria criteria = new AiEvaluationCriteria();
        criteria.setCriteriaCode(metric);
        criteria.setCriteriaName(metric);
        criteria.setType(metric);
        criteria.setFormula(metric);
        criteria.setWeight("response_time_ms".equals(metric) ? 0.5 : 1.0);
        return criteria;
    }

    private ScoreOutcome scoreCriteria(AiEvaluationCriteria criteria, Map<String, Double> metrics) {
        String formula = criteria.getFormula();
        if (formula == null || formula.isBlank()) formula = criteria.getType();
        if (formula == null || formula.isBlank()) formula = criteria.getCriteriaCode();
        if (formula == null || formula.isBlank()) return ScoreOutcome.failure("Criterion has no metric or formula");
        String normalized = formula.trim().toLowerCase(Locale.ROOT);

        if (metrics.containsKey(normalized)) {
            return ScoreOutcome.success(metricToScore(normalized, metrics.get(normalized)),
                    normalized + "=" + metrics.get(normalized));
        }

        Matcher comparison = COMPARISON.matcher(normalized);
        if (comparison.matches()) {
            String metric = comparison.group(1);
            Double value = metrics.get(metric);
            if (value == null) return ScoreOutcome.failure("Unknown metric: " + metric);
            double threshold = Double.parseDouble(comparison.group(3));
            boolean matched = compare(value, comparison.group(2), threshold);
            return ScoreOutcome.success(matched ? 100.0 : 0.0,
                    metric + "=" + value + " " + comparison.group(2) + " " + threshold);
        }

        Matcher multiplication = MULTIPLICATION.matcher(normalized);
        if (multiplication.matches()) {
            String metric = multiplication.group(1);
            Double value = metrics.get(metric);
            if (value == null) return ScoreOutcome.failure("Unknown metric: " + metric);
            return ScoreOutcome.success(clamp(value * Double.parseDouble(multiplication.group(2))),
                    metric + "=" + value);
        }
        return ScoreOutcome.failure("Unsupported formula: " + formula);
    }

    private Map<String, Double> aggregateMetrics(List<AiEvaluationSample> samples) {
        Map<String, Double> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AiEvaluationSample sample : samples) {
            if (sample.getMetricsData() == null || sample.getMetricsData().isBlank()) continue;
            try {
                Map<String, Object> values = objectMapper.readValue(sample.getMetricsData(), new TypeReference<>() {});
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    if (entry.getValue() instanceof Number number) {
                        sums.merge(entry.getKey(), number.doubleValue(), Double::sum);
                        counts.merge(entry.getKey(), 1, Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.warn("Ignoring invalid sample metrics for sample {}", sample.getId(), e);
            }
        }
        Map<String, Double> metrics = new LinkedHashMap<>();
        sums.forEach((key, value) -> metrics.put(key, value / counts.get(key)));
        long failed = samples.stream().filter(sample -> sample.getStatus() != null && sample.getStatus() == 2).count();
        metrics.put("error_rate", (double) failed / samples.size());
        metrics.putIfAbsent("response_time_ms", samples.stream().filter(sample -> sample.getDurationMs() != null)
                .mapToLong(AiEvaluationSample::getDurationMs).average().orElse(0));
        return metrics;
    }

    private Map<String, Double> numericMetrics(Object response) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        if (response instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null && value instanceof Number number) {
                    metrics.put(key.toString().toLowerCase(Locale.ROOT), number.doubleValue());
                }
            });
        }
        return metrics;
    }

    private double metricToScore(String metric, double value) {
        return switch (metric) {
            case "error_rate" -> clamp((1 - value) * 100);
            case "response_time", "response_time_ms", "latency" -> clamp(100 - value / 10.0);
            default -> clamp(value <= 1 ? value * 100 : value);
        };
    }

    private boolean compare(double value, String operator, double threshold) {
        return switch (operator) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "=", "==" -> Double.compare(value, threshold) == 0;
            case "!=" -> Double.compare(value, threshold) != 0;
            default -> false;
        };
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public AiEvaluationCriteria createCriteria(CriteriaConfigRequest request) {
        validateCriteriaRequest(request);
        AiEvaluationCriteria criteria = new AiEvaluationCriteria();
        criteria.setCriteriaCode(request.getCriteriaCode() == null || request.getCriteriaCode().isBlank()
                ? "CR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT)
                : request.getCriteriaCode().trim());
        criteria.setCriteriaName(request.getCriteriaName() == null || request.getCriteriaName().isBlank()
                ? criteria.getCriteriaCode() : request.getCriteriaName().trim());
        criteria.setDescription(request.getDescription());
        criteria.setType(request.getType());
        criteria.setFormula(request.getFormula());
        criteria.setWeight(request.getWeight() == null ? 1.0 : request.getWeight());
        criteria.setThresholds(request.getThresholds());
        criteria.setStatus(1);
        criteria.setCreateTime(LocalDateTime.now());
        criteria.setUpdateTime(LocalDateTime.now());
        criteriaMapper.insert(criteria);
        return criteria;
    }

    public List<AiEvaluationCriteria> getAllCriteria() {
        return criteriaMapper.selectList(new QueryWrapper<AiEvaluationCriteria>().eq("status", 1));
    }

    public boolean updateCriteria(CriteriaConfigRequest request, Long criteriaId) {
        validateCriteriaRequest(request);
        AiEvaluationCriteria criteria = criteriaMapper.selectById(criteriaId);
        if (criteria == null) return false;
        if (request.getCriteriaName() != null) criteria.setCriteriaName(request.getCriteriaName());
        if (request.getDescription() != null) criteria.setDescription(request.getDescription());
        if (request.getType() != null) criteria.setType(request.getType());
        if (request.getFormula() != null) criteria.setFormula(request.getFormula());
        if (request.getWeight() != null) criteria.setWeight(request.getWeight());
        if (request.getThresholds() != null) criteria.setThresholds(request.getThresholds());
        criteria.setUpdateTime(LocalDateTime.now());
        return criteriaMapper.updateById(criteria) > 0;
    }

    public boolean deleteCriteria(Long id) {
        return criteriaMapper.deleteById(id) > 0;
    }

    private void validateCriteriaRequest(CriteriaConfigRequest request) {
        if (request == null) throw new IllegalArgumentException("Criteria request is required");
        if (request.getWeight() != null && request.getWeight() <= 0) {
            throw new IllegalArgumentException("Criteria weight must be positive");
        }
    }

    private record ScoreOutcome(boolean success, Double score, String details) {
        private static ScoreOutcome success(double score, String details) {
            return new ScoreOutcome(true, Math.round(score * 100.0) / 100.0, details);
        }

        private static ScoreOutcome failure(String details) {
            return new ScoreOutcome(false, null, details);
        }
    }
}
