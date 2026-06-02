package com.aipal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@RequiredArgsConstructor
public class CodeQualitySchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS code_quality_standard (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        standard_code VARCHAR(64) NOT NULL,
                        standard_name VARCHAR(128) NOT NULL,
                        description TEXT DEFAULT NULL,
                        language VARCHAR(64) DEFAULT 'GENERAL',
                        framework VARCHAR(64) DEFAULT NULL,
                        status TINYINT NOT NULL DEFAULT 1,
                        gate_config MEDIUMTEXT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_code_quality_standard_code (standard_code),
                        KEY idx_code_quality_standard_status (status, create_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS code_quality_rule (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        standard_id BIGINT NOT NULL,
                        rule_code VARCHAR(64) NOT NULL,
                        category VARCHAR(64) DEFAULT 'maintainability',
                        severity VARCHAR(32) DEFAULT 'MAJOR',
                        title VARCHAR(256) NOT NULL,
                        description TEXT DEFAULT NULL,
                        check_prompt TEXT DEFAULT NULL,
                        enabled TINYINT NOT NULL DEFAULT 1,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        KEY idx_code_quality_rule_standard (standard_id, enabled),
                        KEY idx_code_quality_rule_severity (severity)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_code_quality_run (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        pipeline_id BIGINT NOT NULL,
                        stage_run_id BIGINT NOT NULL,
                        code_stage_run_id BIGINT DEFAULT NULL,
                        standard_id BIGINT DEFAULT NULL,
                        standard_snapshot MEDIUMTEXT DEFAULT NULL,
                        gate_snapshot MEDIUMTEXT DEFAULT NULL,
                        model_code VARCHAR(64) DEFAULT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
                        overall_score INT DEFAULT 0,
                        passed TINYINT NOT NULL DEFAULT 0,
                        summary TEXT DEFAULT NULL,
                        metrics_json MEDIUMTEXT DEFAULT NULL,
                        raw_result MEDIUMTEXT DEFAULT NULL,
                        input_tokens INT DEFAULT 0,
                        output_tokens INT DEFAULT 0,
                        total_tokens INT DEFAULT 0,
                        error_message TEXT DEFAULT NULL,
                        start_time DATETIME DEFAULT NULL,
                        end_time DATETIME DEFAULT NULL,
                        duration_ms INT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_code_quality_run_pipeline (pipeline_id, create_time),
                        KEY idx_code_quality_run_stage (stage_run_id),
                        KEY idx_code_quality_run_status (status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_code_quality_issue (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        run_id BIGINT NOT NULL,
                        pipeline_id BIGINT NOT NULL,
                        stage_run_id BIGINT NOT NULL,
                        rule_code VARCHAR(64) DEFAULT NULL,
                        severity VARCHAR(32) DEFAULT 'MAJOR',
                        category VARCHAR(64) DEFAULT NULL,
                        file_path VARCHAR(512) DEFAULT NULL,
                        line_start INT DEFAULT NULL,
                        line_end INT DEFAULT NULL,
                        title VARCHAR(256) NOT NULL,
                        description TEXT DEFAULT NULL,
                        suggestion TEXT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_code_quality_issue_run (run_id),
                        KEY idx_code_quality_issue_pipeline (pipeline_id),
                        KEY idx_code_quality_issue_severity (severity)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS automation_code_quality_evidence (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        run_id BIGINT NOT NULL,
                        pipeline_id BIGINT NOT NULL,
                        stage_run_id BIGINT NOT NULL,
                        evidence_type VARCHAR(64) NOT NULL,
                        tool_name VARCHAR(128) DEFAULT NULL,
                        command_text VARCHAR(512) DEFAULT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                        score INT DEFAULT 0,
                        summary TEXT DEFAULT NULL,
                        raw_output MEDIUMTEXT DEFAULT NULL,
                        parsed_result_json MEDIUMTEXT DEFAULT NULL,
                        duration_ms INT DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_code_quality_evidence_run (tenant_id, run_id),
                        KEY idx_code_quality_evidence_type (tenant_id, evidence_type),
                        KEY idx_code_quality_evidence_status (tenant_id, status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            addColumnIfMissing(connection, statement, "automation_pipeline", "code_quality_enabled",
                    "ALTER TABLE automation_pipeline ADD COLUMN code_quality_enabled TINYINT NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, statement, "automation_pipeline", "code_quality_standard_id",
                    "ALTER TABLE automation_pipeline ADD COLUMN code_quality_standard_id BIGINT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "automation_pipeline", "code_quality_standard_snapshot",
                    "ALTER TABLE automation_pipeline ADD COLUMN code_quality_standard_snapshot MEDIUMTEXT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "automation_pipeline", "code_quality_gate_snapshot",
                    "ALTER TABLE automation_pipeline ADD COLUMN code_quality_gate_snapshot MEDIUMTEXT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "automation_pipeline", "quality_model_code",
                    "ALTER TABLE automation_pipeline ADD COLUMN quality_model_code VARCHAR(64) DEFAULT NULL");
            statement.executeUpdate("""
                    INSERT INTO code_quality_standard
                        (standard_code, standard_name, description, language, framework, status, gate_config)
                    SELECT 'JAVA_SPRING_VUE_STANDARD', 'Java/Spring/Vue 默认代码标准',
                           '用于评估生成代码的安全性、可维护性、可读性、架构分层、PRD 对齐度和可测试性。',
                           'GENERAL', 'Spring Boot / Vue', 1,
                           '{"overallScoreMin":80,"blockerMax":0,"criticalMax":0,"majorMax":5,"securityScoreMin":0,"prdAlignmentMin":75}'
                    WHERE NOT EXISTS (
                        SELECT 1 FROM code_quality_standard WHERE standard_code = 'JAVA_SPRING_VUE_STANDARD'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO code_quality_rule
                        (standard_id, rule_code, category, severity, title, description, check_prompt, enabled)
                    SELECT s.id, 'SEC-001', 'security', 'BLOCKER', '禁止硬编码敏感信息',
                           '代码中不得硬编码密钥、Token、数据库密码或内部访问凭证。',
                           '检查是否存在硬编码密钥、Token、数据库密码、访问凭证等敏感信息。', 1
                    FROM code_quality_standard s
                    WHERE s.standard_code = 'JAVA_SPRING_VUE_STANDARD'
                      AND NOT EXISTS (SELECT 1 FROM code_quality_rule r WHERE r.standard_id = s.id AND r.rule_code = 'SEC-001')
                    """);
            statement.executeUpdate("""
                    INSERT INTO code_quality_rule
                        (standard_id, rule_code, category, severity, title, description, check_prompt, enabled)
                    SELECT s.id, 'ARCH-001', 'architecture', 'MAJOR', '保持清晰分层',
                           '后端应保持 Controller、Service、DTO/Entity 分层，前端组件职责应清晰。',
                           '检查代码是否存在职责混杂、层级穿透、过度耦合或大型组件问题。', 1
                    FROM code_quality_standard s
                    WHERE s.standard_code = 'JAVA_SPRING_VUE_STANDARD'
                      AND NOT EXISTS (SELECT 1 FROM code_quality_rule r WHERE r.standard_id = s.id AND r.rule_code = 'ARCH-001')
                    """);
            statement.executeUpdate("""
                    INSERT INTO code_quality_rule
                        (standard_id, rule_code, category, severity, title, description, check_prompt, enabled)
                    SELECT s.id, 'MAINT-001', 'maintainability', 'MAJOR', '异常处理与边界校验',
                           '关键入口应包含必要的参数校验、异常处理和可理解的错误信息。',
                           '检查 Controller/API/表单/服务方法是否缺少参数校验、错误处理或日志提示。', 1
                    FROM code_quality_standard s
                    WHERE s.standard_code = 'JAVA_SPRING_VUE_STANDARD'
                      AND NOT EXISTS (SELECT 1 FROM code_quality_rule r WHERE r.standard_id = s.id AND r.rule_code = 'MAINT-001')
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize code quality schema: " + e.getMessage(), e);
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String tableName,
                                    String columnName, String alterSql) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet columns = metadata.getColumns(catalog, null, tableName, columnName)) {
            if (!columns.next()) {
                statement.executeUpdate(alterSql);
            }
        }
    }
}
