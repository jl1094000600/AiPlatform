-- Enterprise memory center: structured memory, policy, versioning, recall trace and feedback.

CREATE TABLE IF NOT EXISTS ai_memory_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    memory_code VARCHAR(64) NOT NULL,
    memory_type VARCHAR(32) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_key VARCHAR(255) NOT NULL,
    project_type VARCHAR(64) DEFAULT NULL,
    project_key VARCHAR(255) DEFAULT NULL,
    owner_user_id BIGINT DEFAULT NULL,
    owner_username VARCHAR(64) DEFAULT NULL,
    title VARCHAR(256) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    fact_json LONGTEXT DEFAULT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref VARCHAR(255) DEFAULT NULL,
    legacy_memory_id BIGINT DEFAULT NULL,
    sensitivity VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    importance INT NOT NULL DEFAULT 50,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 0.5000,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 1,
    valid_from DATETIME DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME DEFAULT NULL,
    last_recalled_at DATETIME DEFAULT NULL,
    recall_count BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_code (memory_code),
    KEY idx_memory_scope_active (tenant_id, scope_type, scope_key, status, expires_at),
    KEY idx_memory_project_active (tenant_id, project_key, status, expires_at),
    KEY idx_memory_owner_active (tenant_id, owner_user_id, status, expires_at),
    KEY idx_memory_source (tenant_id, source_type, source_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_memory_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    scope_type VARCHAR(32) NOT NULL,
    scope_key VARCHAR(255) NOT NULL,
    policy_version INT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    recall_mode VARCHAR(32) NOT NULL DEFAULT 'AUDIT',
    retention_days INT NOT NULL DEFAULT 180,
    allowed_sources VARCHAR(512) DEFAULT NULL,
    max_sensitivity VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    vector_enabled TINYINT NOT NULL DEFAULT 0,
    session_token_budget INT NOT NULL DEFAULT 800,
    working_token_budget INT NOT NULL DEFAULT 300,
    long_term_token_budget INT NOT NULL DEFAULT 500,
    project_token_budget INT NOT NULL DEFAULT 400,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_policy_scope (tenant_id, scope_type, scope_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_memory_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    memory_id BIGINT NOT NULL,
    version INT NOT NULL,
    title VARCHAR(256) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    fact_json LONGTEXT DEFAULT NULL,
    status VARCHAR(32) NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    change_reason VARCHAR(512) DEFAULT NULL,
    changed_by VARCHAR(64) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_version (memory_id, version),
    KEY idx_memory_version_tenant (tenant_id, memory_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_memory_recall_trace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    trace_id VARCHAR(96) NOT NULL,
    user_id BIGINT DEFAULT NULL,
    agent_id BIGINT DEFAULT NULL,
    project_key VARCHAR(255) DEFAULT NULL,
    recall_mode VARCHAR(32) NOT NULL,
    policy_version INT DEFAULT NULL,
    request_summary TEXT DEFAULT NULL,
    candidates_json MEDIUMTEXT DEFAULT NULL,
    injected_json MEDIUMTEXT DEFAULT NULL,
    token_count INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_trace (trace_id),
    KEY idx_memory_trace_scope (tenant_id, project_key, create_time),
    KEY idx_memory_trace_user (tenant_id, user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_memory_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    memory_id BIGINT NOT NULL,
    trace_id VARCHAR(96) DEFAULT NULL,
    feedback_type VARCHAR(32) NOT NULL,
    message VARCHAR(1024) DEFAULT NULL,
    created_by_user_id BIGINT DEFAULT NULL,
    created_by VARCHAR(64) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_memory_feedback_memory (tenant_id, memory_id, create_time),
    KEY idx_memory_feedback_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_memory_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_key VARCHAR(128) NOT NULL,
    project_name VARCHAR(256) NOT NULL,
    project_type VARCHAR(64) NOT NULL DEFAULT 'CUSTOMER_PROJECT',
    owner_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_project_key (tenant_id, project_key),
    KEY idx_memory_project_owner (tenant_id, owner_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_memory_project_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_project_member (project_id, user_id),
    KEY idx_memory_project_member_user (tenant_id, user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
