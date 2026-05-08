package com.aipal.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentQualityMetricCalculatorTest {

    private final AgentQualityMetricCalculator calculator = new AgentQualityMetricCalculator();

    @Test
    void calculatesPerfectMetrics() {
        AgentQualityMetricCalculator.Metrics metrics = calculator.calculate(List.of(
                record("yes", "yes"),
                record("no", "no")
        ));

        assertEquals(100.0, metrics.getAccuracy());
        assertEquals(100.0, metrics.getPrecisionScore());
        assertEquals(100.0, metrics.getRecallScore());
        assertEquals(100.0, metrics.getF1Score());
    }

    @Test
    void calculatesPartialMacroMetrics() {
        AgentQualityMetricCalculator.Metrics metrics = calculator.calculate(List.of(
                record("A", "A"),
                record("A", "B"),
                record("B", "B")
        ));

        assertEquals(66.67, metrics.getAccuracy());
        assertEquals(75.0, metrics.getPrecisionScore());
        assertEquals(75.0, metrics.getRecallScore());
        assertEquals(66.67, metrics.getF1Score());
    }

    @Test
    void handlesEmptyDataset() {
        AgentQualityMetricCalculator.Metrics metrics = calculator.calculate(List.of());

        assertEquals(0.0, metrics.getAccuracy());
        assertEquals(0.0, metrics.getPrecisionScore());
        assertEquals(0.0, metrics.getRecallScore());
        assertEquals(0.0, metrics.getF1Score());
    }

    private AgentQualityMetricCalculator.MatchRecord record(String expected, String predicted) {
        return new AgentQualityMetricCalculator.MatchRecord(expected, predicted, expected.equals(predicted));
    }
}
