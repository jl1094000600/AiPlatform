-- Immutable published workflow snapshots (Graph DSL v1).
-- Draft definition stays in ai_workflow.workflow_definition; published versions are append-only.
CREATE TABLE IF NOT EXISTS ai_workflow_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    workflow_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    definition_json JSON NOT NULL,
    layout_json JSON DEFAULT NULL,
    definition_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    published_by BIGINT DEFAULT NULL,
    publish_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_version (tenant_id, workflow_id, version_no),
    KEY idx_workflow_version_status (tenant_id, workflow_id, status, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Run binding columns (version_id, definition_hash) are added by
-- WorkflowVersionSchemaInitializer / TenantSecuritySchemaInitializer at app startup.

-- Node-level run traces for workflow executions (production + DRY_RUN try-runs).
-- input/output are stored as SHA-256 digests only; raw sensitive values never persist.
CREATE TABLE IF NOT EXISTS ai_workflow_node_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    execution_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    attempt INT NOT NULL DEFAULT 1,
    input_digest VARCHAR(64) DEFAULT NULL,
    output_digest VARCHAR(64) DEFAULT NULL,
    duration_ms BIGINT DEFAULT NULL,
    error_message VARCHAR(2000) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_workflow_node_run_exec (tenant_id, execution_id, id),
    KEY idx_workflow_node_run_node (tenant_id, execution_id, node_id, attempt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Draft canvas layout is persisted server-side so drafts survive browser cache clears.
ALTER TABLE ai_workflow ADD COLUMN layout_json JSON DEFAULT NULL;

