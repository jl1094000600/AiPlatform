package com.aipal.service;

import com.aipal.entity.AiEvaluation;
import com.aipal.mapper.AiEvaluationMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationStatisticsService {

    private final AiEvaluationMapper evaluationMapper;

    public Map<String, Object> getEvaluationStatistics(Long datasetId, Long agentId, String timeRange) {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<AiEvaluation> wrapper = new QueryWrapper<>();
        if (datasetId != null) {
            wrapper.eq("dataset_id", datasetId);
        }
        if (agentId != null) {
            wrapper.eq("agent_id", agentId);
        }
        if (timeRange != null) {
            LocalDateTime startTime = parseTimeRange(timeRange);
            wrapper.ge("create_time", startTime);
        }
        wrapper.eq("status", 2);

        List<AiEvaluation> evaluations = evaluationMapper.selectList(wrapper);

        stats.put("totalEvaluations", evaluations.size());
        stats.put("avgScore", calculateAverageScore(evaluations));
        stats.put("maxScore", calculateMaxScore(evaluations));
        stats.put("minScore", calculateMinScore(evaluations));
        stats.put("passRate", calculatePassRate(evaluations));
        stats.put("scoreDistribution", calculateScoreDistribution(evaluations));
        stats.put("trendData", calculateTrendData(evaluations));

        return stats;
    }

    public Map<String, Object> getAgentLeaderboard(int topN) {
        QueryWrapper<AiEvaluation> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 2);
        wrapper.orderByDesc("total_score");
        wrapper.last("LIMIT " + topN);

        List<AiEvaluation> topEvaluations = evaluationMapper.selectList(wrapper);

        Map<Long, Double> agentScores = topEvaluations.stream()
                .collect(Collectors.groupingBy(
                        AiEvaluation::getAgentId,
                        Collectors.averagingDouble(AiEvaluation::getTotalScore)
                ));

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        agentScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .forEach(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("agentId", entry.getKey());
                    item.put("avgScore", entry.getValue());
                    item.put("evaluationCount", countEvaluationsByAgent(entry.getKey()));
                    leaderboard.add(item);
                });

        Map<String, Object> result = new HashMap<>();
        result.put("leaderboard", leaderboard);
        return result;
    }

    public Map<String, Object> getDatasetRanking(int topN) {
        QueryWrapper<AiEvaluation> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 2);

        List<AiEvaluation> evaluations = evaluationMapper.selectList(wrapper);

        Map<Long, Double> datasetScores = evaluations.stream()
                .collect(Collectors.groupingBy(
                        AiEvaluation::getDatasetId,
                        Collectors.averagingDouble(e -> e.getTotalScore() != null ? e.getTotalScore() : 0)
                ));

        List<Map<String, Object>> ranking = new ArrayList<>();
        datasetScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .forEach(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("datasetId", entry.getKey());
                    item.put("avgScore", entry.getValue());
                    item.put("evaluationCount", countEvaluationsByDataset(entry.getKey()));
                    ranking.add(item);
                });

        Map<String, Object> result = new HashMap<>();
        result.put("ranking", ranking);
        return result;
    }

    public String generateEvaluationReport(Long evaluationId) {
        AiEvaluation evaluation = evaluationMapper.selectById(evaluationId);
        if (evaluation == null) {
            return null;
        }

        StringBuilder report = new StringBuilder();
        report.append("=== Evaluation Report ===\n");
        report.append("Evaluation Code: ").append(evaluation.getEvaluationCode()).append("\n");
        report.append("Dataset ID: ").append(evaluation.getDatasetId()).append("\n");
        report.append("Agent ID: ").append(evaluation.getAgentId()).append("\n");
        report.append("Total Score: ").append(evaluation.getTotalScore()).append("\n");
        report.append("Status: ").append(evaluation.getStatus() == 2 ? "Completed" : "Failed").append("\n");
        report.append("Start Time: ").append(evaluation.getStartTime()).append("\n");
        report.append("End Time: ").append(evaluation.getEndTime()).append("\n");
        report.append("========================\n");

        log.info("Report generated for evaluation: {}", evaluation.getEvaluationCode());
        return report.toString();
    }

    private double calculateAverageScore(List<AiEvaluation> evaluations) {
        if (evaluations.isEmpty()) return 0;
        return evaluations.stream()
                .filter(e -> e.getTotalScore() != null)
                .mapToDouble(AiEvaluation::getTotalScore)
                .average()
                .orElse(0);
    }

    private double calculateMaxScore(List<AiEvaluation> evaluations) {
        return evaluations.stream()
                .filter(e -> e.getTotalScore() != null)
                .mapToDouble(AiEvaluation::getTotalScore)
                .max()
                .orElse(0);
    }

    private double calculateMinScore(List<AiEvaluation> evaluations) {
        return evaluations.stream()
                .filter(e -> e.getTotalScore() != null)
                .mapToDouble(AiEvaluation::getTotalScore)
                .min()
                .orElse(0);
    }

    private double calculatePassRate(List<AiEvaluation> evaluations) {
        if (evaluations.isEmpty()) return 0;
        long passCount = evaluations.stream()
                .filter(e -> e.getTotalScore() != null && e.getTotalScore() >= 60)
                .count();
        return (double) passCount / evaluations.size() * 100;
    }

    private Map<String, Integer> calculateScoreDistribution(List<AiEvaluation> evaluations) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("0-20", 0);
        distribution.put("20-40", 0);
        distribution.put("40-60", 0);
        distribution.put("60-80", 0);
        distribution.put("80-100", 0);

        for (AiEvaluation evaluation : evaluations) {
            if (evaluation.getTotalScore() == null) continue;
            double score = evaluation.getTotalScore();
            if (score < 20) distribution.merge("0-20", 1, Integer::sum);
            else if (score < 40) distribution.merge("20-40", 1, Integer::sum);
            else if (score < 60) distribution.merge("40-60", 1, Integer::sum);
            else if (score < 80) distribution.merge("60-80", 1, Integer::sum);
            else distribution.merge("80-100", 1, Integer::sum);
        }

        return distribution;
    }

    private List<Map<String, Object>> calculateTrendData(List<AiEvaluation> evaluations) {
        Map<String, List<Double>> dailyScores = new HashMap<>();

        for (AiEvaluation evaluation : evaluations) {
            if (evaluation.getCreateTime() == null || evaluation.getTotalScore() == null) continue;
            String date = evaluation.getCreateTime().toLocalDate().toString();
            dailyScores.computeIfAbsent(date, k -> new ArrayList<>()).add(evaluation.getTotalScore());
        }

        return dailyScores.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("date", entry.getKey());
                    point.put("avgScore", entry.getValue().stream().mapToDouble(d -> d).average().orElse(0));
                    point.put("count", entry.getValue().size());
                    return point;
                })
                .collect(Collectors.toList());
    }

    private long countEvaluationsByAgent(Long agentId) {
        return evaluationMapper.selectCount(new QueryWrapper<AiEvaluation>().eq("agent_id", agentId));
    }

    private long countEvaluationsByDataset(Long datasetId) {
        return evaluationMapper.selectCount(new QueryWrapper<AiEvaluation>().eq("dataset_id", datasetId));
    }

    private LocalDateTime parseTimeRange(String timeRange) {
        LocalDateTime now = LocalDateTime.now();
        return switch (timeRange.toLowerCase()) {
            case "today" -> now.toLocalDate().atStartOfDay();
            case "week" -> now.minusDays(7);
            case "month" -> now.minusMonths(1);
            case "quarter" -> now.minusMonths(3);
            default -> now.minusYears(1);
        };
    }
}