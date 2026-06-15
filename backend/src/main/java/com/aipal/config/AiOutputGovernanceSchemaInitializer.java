package com.aipal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@ConditionalOnProperty(name = "aipal.schema-initialization.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AiOutputGovernanceSchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ai_output_governance_record (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        record_code VARCHAR(64) NOT NULL,
                        source_type VARCHAR(64) NOT NULL DEFAULT 'AUTOMATION_PIPELINE',
                        pipeline_id BIGINT DEFAULT NULL,
                        stage_run_id BIGINT DEFAULT NULL,
                        generation_job_id BIGINT DEFAULT NULL,
                        stage_key VARCHAR(64) DEFAULT NULL,
                        artifact_type VARCHAR(64) NOT NULL,
                        artifact_path VARCHAR(1024) DEFAULT NULL,
                        artifact_summary TEXT DEFAULT NULL,
                        model_code VARCHAR(64) DEFAULT NULL,
                        governance_status VARCHAR(32) NOT NULL DEFAULT 'NEEDS_REVIEW',
                        risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW',
                        risk_score INT DEFAULT 0,
                        policy_snapshot MEDIUMTEXT DEFAULT NULL,
                        metadata_json MEDIUMTEXT DEFAULT NULL,
                        input_tokens INT DEFAULT NULL,
                        output_tokens INT DEFAULT NULL,
                        total_tokens INT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_ai_output_record_code (record_code),
                        KEY idx_ai_output_pipeline (pipeline_id, create_time),
                        KEY idx_ai_output_stage (stage_run_id),
                        KEY idx_ai_output_type (artifact_type, create_time),
                        KEY idx_ai_output_risk (risk_level, governance_status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ai_output_governance_policy_template (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        policy_code VARCHAR(64) NOT NULL,
                        policy_name VARCHAR(128) NOT NULL,
                        description TEXT DEFAULT NULL,
                        category VARCHAR(64) NOT NULL DEFAULT 'security',
                        severity VARCHAR(32) NOT NULL DEFAULT 'MAJOR',
                        target_artifact_type VARCHAR(64) NOT NULL DEFAULT 'CODE',
                        detector_type VARCHAR(64) NOT NULL DEFAULT 'REGEX',
                        config_json MEDIUMTEXT DEFAULT NULL,
                        block_on_match TINYINT NOT NULL DEFAULT 1,
                        status TINYINT NOT NULL DEFAULT 1,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_ai_policy_template_code (policy_code),
                        KEY idx_ai_policy_template_status (status, category),
                        KEY idx_ai_policy_template_target (target_artifact_type, status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            seedPolicyTemplates(statement);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize AI output governance schema: " + e.getMessage(), e);
        }
    }

    private void seedPolicyTemplates(Statement statement) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO ai_output_governance_policy_template
                    (policy_code, policy_name, description, category, severity, target_artifact_type,
                     detector_type, config_json, block_on_match, status)
                SELECT 'SECRET_SCAN', '敏感信息拦截',
                       '检测生成代码中可能出现的密钥、Token、密码、私钥等敏感信息。',
                       'security', 'BLOCKER', 'CODE', 'REGEX',
                       '{"pattern":"(?i)(api[_-]?key|secret|token|password|private[_-]?key)\\\\s*[:=]\\\\s*[A-Za-z0-9_\\\\-]{12,}"}',
                       1, 1
                WHERE NOT EXISTS (
                    SELECT 1 FROM ai_output_governance_policy_template WHERE policy_code = 'SECRET_SCAN'
                )
                """);
        statement.executeUpdate("""
                INSERT INTO ai_output_governance_policy_template
                    (policy_code, policy_name, description, category, severity, target_artifact_type,
                     detector_type, config_json, block_on_match, status)
                SELECT 'UNAUTHORIZED_TOOL_CALL', '越权工具调用拦截',
                       '检测生成代码中直接触发系统命令、进程执行或脚本注入的高风险调用。',
                       'tool_permission', 'CRITICAL', 'CODE', 'KEYWORD',
                       '{"keywords":["Runtime.getRuntime","ProcessBuilder","child_process","exec(","eval(","shell=True"]}',
                       1, 1
                WHERE NOT EXISTS (
                    SELECT 1 FROM ai_output_governance_policy_template WHERE policy_code = 'UNAUTHORIZED_TOOL_CALL'
                )
                """);
        statement.executeUpdate("""
                INSERT INTO ai_output_governance_policy_template
                    (policy_code, policy_name, description, category, severity, target_artifact_type,
                     detector_type, config_json, block_on_match, status)
                SELECT 'MISSING_TESTS', '未测代码提醒',
                       '代码产物中没有测试文件时标记为需要关注，不直接阻塞交付。',
                       'testability', 'MAJOR', 'CODE', 'MISSING_TEST',
                       '{"testPathKeywords":["test","spec","__tests__"]}',
                       0, 1
                WHERE NOT EXISTS (
                    SELECT 1 FROM ai_output_governance_policy_template WHERE policy_code = 'MISSING_TESTS'
                )
                """);
        statement.executeUpdate("""
                INSERT INTO ai_output_governance_policy_template
                    (policy_code, policy_name, description, category, severity, target_artifact_type,
                     detector_type, config_json, block_on_match, status)
                SELECT 'HIGH_RISK_OUTPUT', '高风险产物阻塞',
                       '当治理风险分达到高风险区间时自动阻塞审批放行。',
                       'governance', 'CRITICAL', 'ANY', 'HIGH_RISK',
                       '{"riskScoreMin":80}',
                       1, 1
                WHERE NOT EXISTS (
                    SELECT 1 FROM ai_output_governance_policy_template WHERE policy_code = 'HIGH_RISK_OUTPUT'
                )
                """);
    }
}
