CREATE TABLE IF NOT EXISTS project_discussion_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_key VARCHAR(128) NOT NULL,
    sender_user_id BIGINT DEFAULT NULL,
    sender_name VARCHAR(128) DEFAULT NULL,
    message_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    idempotency_key VARCHAR(128) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_discussion_idempotency (tenant_id, project_key, idempotency_key),
    KEY idx_project_discussion_message (tenant_id, project_key, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_task_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_key VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT DEFAULT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT DEFAULT NULL,
    suggested_agent_id BIGINT DEFAULT NULL,
    suggested_assignee_user_id BIGINT DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    confirmed_run_id BIGINT DEFAULT NULL,
    idempotency_key VARCHAR(128) DEFAULT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_task_draft_idempotency (tenant_id, project_key, idempotency_key),
    KEY idx_project_task_draft (tenant_id, project_key, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_key VARCHAR(128) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    assignee_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED',
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_assignment_target (tenant_id, target_type, target_id),
    KEY idx_project_assignment (tenant_id, project_key, assignee_user_id, status, update_time),
    KEY idx_project_assignment_mine (tenant_id, assignee_user_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_assignment_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_key VARCHAR(128) NOT NULL,
    assignment_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_assignee_user_id BIGINT DEFAULT NULL,
    to_assignee_user_id BIGINT DEFAULT NULL,
    from_status VARCHAR(32) DEFAULT NULL,
    to_status VARCHAR(32) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_name VARCHAR(128) DEFAULT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_project_assignment_event (tenant_id, assignment_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
