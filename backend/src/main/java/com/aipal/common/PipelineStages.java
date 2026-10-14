package com.aipal.common;

/**
 * Canonical automation pipeline stage keys shared across services and DTOs.
 */
public final class PipelineStages {

    public static final String REQUIREMENT_ANALYSIS = "requirement_analysis";
    public static final String CODE_GENERATION = "code_generation";
    public static final String CODE_QUALITY_EVALUATION = "code_quality_evaluation";
    public static final String BUILD_COMPILE = "build_compile";
    public static final String TEST_EXECUTION = "test_execution";
    public static final String DEPLOYMENT_RELEASE = "deployment_release";
    public static final String OPERATIONS_MONITORING = "operations_monitoring";
    public static final String DELIVERY_REPORT = "delivery_report";

    private PipelineStages() {
    }
}
