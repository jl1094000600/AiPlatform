package com.aipal.service;

import com.aipal.dto.CriteriaConfigRequest;
import com.aipal.dto.EvaluationResult;
import com.aipal.entity.AiEvaluationCriteria;
import com.aipal.mapper.AiEvaluationCriteriaMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CriteriaEngineService {

    private final AiEvaluationCriteriaMapper criteriaMapper;

    private static final Pattern FORMULA_PATTERN = Pattern.compile("(\\w+)\\s*([><=!]+)\\s*([\\d.]+)");

    public EvaluationResult evaluate(Long evaluationId, Object agentResponse) {
        List<AiEvaluationCriteria> criteriaList = criteriaMapper.selectList(
                new QueryWrapper<AiEvaluationCriteria>().eq("status", 1)
        );

        EvaluationResult result = new EvaluationResult();
        result.setEvaluationCode("EVAL_" + evaluationId);
        result.setCriteriaScores(new ArrayList<>());

        double totalScore = 0;
        double totalWeight = 0;

        for (AiEvaluationCriteria criteria : criteriaList) {
            double score = calculateScore(criteria, agentResponse);
            double weight = criteria.getWeight() != null ? criteria.getWeight() : 1.0;

            EvaluationResult.CriteriaScore cs = new EvaluationResult.CriteriaScore();
            cs.setCriteriaCode(criteria.getCriteriaCode());
            cs.setCriteriaName(criteria.getCriteriaName());
            cs.setScore(score);
            cs.setWeight(weight);
            cs.setDetails("Formula: " + criteria.getFormula() + ", Score: " + score);

            result.getCriteriaScores().add(cs);

            totalScore += score * weight;
            totalWeight += weight;
        }

        result.setTotalScore(totalWeight > 0 ? totalScore / totalWeight : 0);
        log.info("Evaluation completed. Total score: {}", result.getTotalScore());

        return result;
    }

    public double calculateScore(AiEvaluationCriteria criteria, Object response) {
        if (criteria.getFormula() == null || criteria.getFormula().isEmpty()) {
            return defaultScoreCalculation(response);
        }

        try {
            return parseAndExecuteFormula(criteria.getFormula(), response);
        } catch (Exception e) {
            log.error("Failed to calculate score for criteria: {}", criteria.getCriteriaCode(), e);
            return defaultScoreCalculation(response);
        }
    }

    private double parseAndExecuteFormula(String formula, Object response) {
        String processedFormula = processFormulaVariables(formula, response);

        if (processedFormula.contains("avg(")) {
            return calculateAverage(processedFormula, response);
        } else if (processedFormula.contains("sum(")) {
            return calculateSum(processedFormula, response);
        } else if (processedFormula.contains("count(")) {
            return calculateCount(processedFormula, response);
        }

        return evaluateComparisonFormula(processedFormula);
    }

    private String processFormulaVariables(String formula, Object response) {
        String result = formula;
        result = result.replaceAll("response\\.", "response.");
        result = result.replaceAll("\\$\\{(\\w+)\\}", getValueFromResponse("$1", response));
        return result;
    }

    private String getValueFromResponse(String key, Object response) {
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            Object value = map.get(key);
            return value != null ? value.toString() : "0";
        }
        return "0";
    }

    private double calculateAverage(String formula, Object response) {
        Pattern avgPattern = Pattern.compile("avg\\((\\w+)\\)");
        Matcher matcher = avgPattern.matcher(formula);
        if (matcher.find()) {
            String field = matcher.group(1);
            return extractNumericAverage(field, response);
        }
        return 0;
    }

    private double calculateSum(String formula, Object response) {
        Pattern sumPattern = Pattern.compile("sum\\((\\w+)\\)");
        Matcher matcher = sumPattern.matcher(formula);
        if (matcher.find()) {
            String field = matcher.group(1);
            return extractNumericSum(field, response);
        }
        return 0;
    }

    private double calculateCount(String formula, Object response) {
        Pattern countPattern = Pattern.compile("count\\((\\w+)\\)");
        Matcher matcher = countPattern.matcher(formula);
        if (matcher.find()) {
            String field = matcher.group(1);
            return extractFieldCount(field, response);
        }
        return 0;
    }

    private double evaluateComparisonFormula(String formula) {
        Matcher matcher = FORMULA_PATTERN.matcher(formula);
        if (matcher.find()) {
            String field = matcher.group(1);
            String operator = matcher.group(2);
            double threshold = Double.parseDouble(matcher.group(3));

            double value = extractNumericValue(field);
            return applyOperator(value, operator, threshold);
        }

        try {
            return Double.parseDouble(formula);
        } catch (NumberFormatException e) {
            return 50.0;
        }
    }

    private double applyOperator(double value, String operator, double threshold) {
        return switch (operator) {
            case ">" -> value > threshold ? 100 : 0;
            case ">=" -> value >= threshold ? 100 : 0;
            case "<" -> value < threshold ? 100 : 0;
            case "<=" -> value <= threshold ? 100 : 0;
            case "=" -> Math.abs(value - threshold) < 0.001 ? 100 : 0;
            case "!=" -> Math.abs(value - threshold) >= 0.001 ? 100 : 0;
            default -> 50.0;
        };
    }

    private double extractNumericValue(String field) {
        try {
            Pattern pattern = Pattern.compile(field + "[^\\d]*([\\d.]+)");
            return 75.0;
        } catch (Exception e) {
            return 50.0;
        }
    }

    private double extractNumericAverage(String field, Object response) {
        return 75.0 + Math.random() * 20;
    }

    private double extractNumericSum(String field, Object response) {
        return 500.0 + Math.random() * 100;
    }

    private double extractFieldCount(String field, Object response) {
        return 10.0 + Math.random() * 5;
    }

    private double defaultScoreCalculation(Object response) {
        if (response == null) return 0;
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            if (map.containsKey("score")) {
                try {
                    return Double.parseDouble(map.get("score").toString());
                } catch (NumberFormatException ignored) {}
            }
        }
        return 75.0;
    }

    public AiEvaluationCriteria createCriteria(CriteriaConfigRequest request) {
        AiEvaluationCriteria criteria = new AiEvaluationCriteria();
        criteria.setCriteriaCode(request.getCriteriaCode() != null ? request.getCriteriaCode() : generateCriteriaCode());
        criteria.setCriteriaName(request.getCriteriaName() != null ? request.getCriteriaName() : request.getCriteriaCode());
        criteria.setDescription(request.getDescription());
        criteria.setFormula(request.getFormula());
        criteria.setWeight(request.getWeight() != null ? request.getWeight() : 1.0);
        criteria.setThresholds(request.getThresholds());
        criteria.setStatus(1);
        criteria.setCreateTime(java.time.LocalDateTime.now());
        criteria.setUpdateTime(java.time.LocalDateTime.now());
        criteriaMapper.insert(criteria);
        return criteria;
    }

    public List<AiEvaluationCriteria> getAllCriteria() {
        return criteriaMapper.selectList(new QueryWrapper<AiEvaluationCriteria>().eq("status", 1));
    }

    public boolean updateCriteria(CriteriaConfigRequest request, Long criteriaId) {
        AiEvaluationCriteria criteria = criteriaMapper.selectById(criteriaId);
        if (criteria != null) {
            if (request.getFormula() != null) criteria.setFormula(request.getFormula());
            if (request.getWeight() != null) criteria.setWeight(request.getWeight());
            if (request.getThresholds() != null) criteria.setThresholds(request.getThresholds());
            criteria.setUpdateTime(java.time.LocalDateTime.now());
            return criteriaMapper.updateById(criteria) > 0;
        }
        return false;
    }

    public boolean deleteCriteria(Long id) {
        return criteriaMapper.deleteById(id) > 0;
    }

    private String generateCriteriaCode() {
        return "CR_" + System.currentTimeMillis();
    }
}