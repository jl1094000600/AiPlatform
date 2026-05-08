package com.aipal.service;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AgentQualityMetricCalculator {

    public Metrics calculate(List<MatchRecord> records) {
        Metrics metrics = new Metrics();
        if (records == null || records.isEmpty()) {
            return metrics;
        }

        long matches = records.stream().filter(MatchRecord::isMatched).count();
        metrics.setAccuracy(percent((double) matches / records.size()));

        Set<String> labels = new HashSet<>();
        for (MatchRecord record : records) {
            labels.add(record.getExpected());
            labels.add(record.getPredicted());
        }
        labels.remove("");

        if (labels.isEmpty()) {
            metrics.setPrecisionScore(metrics.getAccuracy());
            metrics.setRecallScore(metrics.getAccuracy());
            metrics.setF1Score(metrics.getAccuracy());
            return metrics;
        }

        double precisionTotal = 0;
        double recallTotal = 0;
        double f1Total = 0;
        for (String label : labels) {
            long tp = records.stream()
                    .filter(r -> label.equals(r.getExpected()) && label.equals(r.getPredicted()))
                    .count();
            long fp = records.stream()
                    .filter(r -> !label.equals(r.getExpected()) && label.equals(r.getPredicted()))
                    .count();
            long fn = records.stream()
                    .filter(r -> label.equals(r.getExpected()) && !label.equals(r.getPredicted()))
                    .count();

            double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
            double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
            double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
            precisionTotal += precision;
            recallTotal += recall;
            f1Total += f1;
        }

        metrics.setPrecisionScore(percent(precisionTotal / labels.size()));
        metrics.setRecallScore(percent(recallTotal / labels.size()));
        metrics.setF1Score(percent(f1Total / labels.size()));
        return metrics;
    }

    public String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private double percent(double value) {
        return BigDecimal.valueOf(value * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @Data
    public static class MatchRecord {
        private final String expected;
        private final String predicted;
        private final boolean matched;
    }

    @Data
    public static class Metrics {
        private double accuracy;
        private double precisionScore;
        private double recallScore;
        private double f1Score;
    }
}
