package com.aipal.common;

/**
 * Canonical status vocabulary for automation pipeline, stage and generation job entities.
 */
public final class PipelineStatus {

    public static final String PENDING = "PENDING";
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String WAITING_APPROVAL = "WAITING_APPROVAL";
    public static final String SUCCESS = "SUCCESS";
    public static final String REJECTED = "REJECTED";
    public static final String BLOCKED = "BLOCKED";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String SUCCEEDED = "SUCCEEDED";

    private PipelineStatus() {
    }
}
