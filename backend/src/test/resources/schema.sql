CREATE TABLE IF NOT EXISTS ai_agent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    agent_code VARCHAR(64),
    agent_name VARCHAR(128),
    category VARCHAR(64),
    description VARCHAR(512),
    api_url VARCHAR(256),
    http_method VARCHAR(16),
    request_schema CLOB,
    response_schema CLOB,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    is_deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ai_agent_heartbeat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    agent_id BIGINT,
    agent_code VARCHAR(64),
    instance_id VARCHAR(64),
    last_heartbeat TIMESTAMP,
    health_score INT,
    endpoint VARCHAR(256),
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_a2a_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    task_id VARCHAR(64),
    session_id VARCHAR(64),
    task_type VARCHAR(32),
    task_description CLOB,
    source_agent_id BIGINT,
    target_agent_id BIGINT,
    status VARCHAR(32),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_message CLOB,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    is_deleted TINYINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS mon_call_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    trace_id VARCHAR(64),
    agent_id BIGINT,
    agent_version VARCHAR(64),
    biz_module_id BIGINT,
    user_id BIGINT,
    username VARCHAR(128),
    model_id BIGINT,
    status_code INT,
    success TINYINT,
    duration_ms INT,
    input_tokens INT,
    output_tokens INT,
    total_tokens INT,
    request_time TIMESTAMP,
    response_time TIMESTAMP,
    error_message CLOB,
    request_params CLOB,
    response_result CLOB,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    is_deleted TINYINT DEFAULT 0
);
