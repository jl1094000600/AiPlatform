package com.aipal.dto;

import lombok.Data;
import java.util.List;

@Data
public class EvaluationResult {
    private String evaluationCode;
    private Double totalScore;
    private List<CriteriaScore> criteriaScores;
    private String reportPath;

    @Data
    public static class CriteriaScore {
        private String criteriaCode;
        private String criteriaName;
        private Double score;
        private Double weight;
        private String details;
    }
}