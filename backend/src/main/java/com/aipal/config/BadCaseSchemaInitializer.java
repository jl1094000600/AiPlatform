package com.aipal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@ConditionalOnProperty(name = "aipal.schema-initialization.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BadCaseSchemaInitializer {
    private final DataSource dataSource;

    private static final String[][] PRD_TYPES = {
            {"REQUIREMENT_HALLUCINATION", "The PRD invents business actors, integrations, or workflow steps that were not provided."},
            {"MISSING_BUSINESS_GOAL", "The PRD lists features but does not define the target outcome or measurable success metric."},
            {"ROLE_CONFUSION", "The PRD mixes operator, admin, reviewer, and end-user responsibilities."},
            {"CONFLICTING_REQUIREMENTS", "The PRD accepts mutually conflicting constraints without calling out trade-offs."},
            {"MISSING_ACCEPTANCE_CRITERIA", "The PRD cannot be reviewed because acceptance criteria are absent or vague."},
            {"MVP_SCOPE_CREEP", "The PRD expands a small request into a large platform without an MVP boundary."},
            {"SECURITY_OMISSION", "The PRD omits sensitive data protection, permission, audit, or compliance requirements."},
            {"ERROR_FLOW_MISSING", "The PRD only covers the happy path and ignores failure, retry, rollback, or recovery."},
            {"DATA_CONTRACT_GAP", "The PRD does not specify required fields, status values, validation rules, or ownership."},
            {"QUALITY_LOOP_MISSING", "The PRD does not describe feedback capture, badcase labeling, or version comparison."}
    };

    private static final String[][] CODE_TYPES = {
            {"NON_RUNNABLE_CODE", "Generated code misses imports, dependencies, configuration, or entry wiring."},
            {"TECH_STACK_MISMATCH", "Generated code uses a framework or style that does not match the existing project."},
            {"API_HALLUCINATION", "Generated code calls endpoints, fields, or SDK methods that do not exist."},
            {"DATA_SCHEMA_MISMATCH", "Generated code uses names, types, or required flags that differ from the PRD."},
            {"ERROR_HANDLING_MISSING", "Generated code handles only the happy path and drops timeout, empty, or failed states."},
            {"SECURITY_RISK", "Generated code exposes secrets, skips auth checks, or accepts unsafe user input."},
            {"PERMISSION_BYPASS", "Generated code hides UI actions but does not enforce backend permission checks."},
            {"PERFORMANCE_RISK", "Generated code loads large datasets synchronously or performs repeated expensive queries."},
            {"UNSCOPED_CHANGE", "Generated code changes unrelated files, global styles, or shared contracts."},
            {"TEST_GAP", "Generated code changes behavior without focused unit, API, or UI verification."}
    };

    private static final String[][] E2E_TYPES = {
            {"PRD_CODE_FIELD_DRIFT", "The code introduces fields, states, or modules not present in the approved PRD."},
            {"PRD_EXCEPTION_NOT_IMPLEMENTED", "The PRD defines an exception flow but the generated code omits it."},
            {"PRD_PERMISSION_NOT_IMPLEMENTED", "The PRD defines role permissions but the generated code enforces them incompletely."},
            {"PRD_METRIC_NOT_INSTRUMENTED", "The PRD defines measurable quality metrics but the code has no tracking point."},
            {"PRD_NAMING_INCONSISTENCY", "Business nouns in the PRD are renamed inconsistently in routes, models, or UI labels."}
    };

    private static final String[] PRD_SCENARIOS = {
            "AI customer support console", "code generation workspace", "enterprise approval center", "dataset evaluation dashboard"
    };

    private static final String[] CODE_SCENARIOS = {
            "login and permission module", "file upload endpoint", "generated code preview panel", "tenant billing report"
    };

    private static final String[] E2E_SCENARIOS = {
            "PRD-to-code delivery pipeline", "manual review workflow", "generated artifact archive", "badcase analysis loop"
    };

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS bad_case_record (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        case_code VARCHAR(64) NOT NULL,
                        source_type VARCHAR(32) NOT NULL,
                        stage VARCHAR(32) NOT NULL,
                        badcase_type VARCHAR(64) NOT NULL,
                        severity VARCHAR(16) NOT NULL,
                        project_name VARCHAR(128) DEFAULT NULL,
                        requirement_title VARCHAR(256) DEFAULT NULL,
                        input_prompt MEDIUMTEXT DEFAULT NULL,
                        generated_prd MEDIUMTEXT DEFAULT NULL,
                        generated_code MEDIUMTEXT DEFAULT NULL,
                        expected_behavior MEDIUMTEXT DEFAULT NULL,
                        failure_reason MEDIUMTEXT DEFAULT NULL,
                        reviewed_by VARCHAR(64) DEFAULT NULL,
                        pipeline_id BIGINT DEFAULT NULL,
                        stage_run_id BIGINT DEFAULT NULL,
                        batch_id BIGINT DEFAULT NULL,
                        feedback_id BIGINT DEFAULT NULL,
                        approval_id BIGINT DEFAULT NULL,
                        tags VARCHAR(512) DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_bad_case_code (case_code),
                        KEY idx_bad_case_tenant_stage (tenant_id, stage, create_time),
                        KEY idx_bad_case_stage (stage, create_time),
                        KEY idx_bad_case_type (badcase_type),
                        KEY idx_bad_case_source (source_type),
                        KEY idx_bad_case_pipeline (pipeline_id, stage_run_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            addTenantColumnIfMissing(connection, statement);
            seedColdStartCases(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize badcase schema: " + e.getMessage(), e);
        }
    }

    private void addTenantColumnIfMissing(Connection connection, Statement statement) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'bad_case_record'
                  AND COLUMN_NAME = 'tenant_id'
                """);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            if (resultSet.next() && resultSet.getLong(1) == 0) {
                statement.executeUpdate("ALTER TABLE bad_case_record ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id");
                statement.executeUpdate("ALTER TABLE bad_case_record ADD KEY idx_bad_case_tenant_stage (tenant_id, stage, create_time)");
            }
        }
        statement.executeUpdate("UPDATE bad_case_record SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0");
    }

    private void seedColdStartCases(Connection connection) throws SQLException {
        try (PreparedStatement count = connection.prepareStatement(
                "SELECT COUNT(*) FROM bad_case_record WHERE case_code LIKE 'BC-COLD-%'");
             ResultSet resultSet = count.executeQuery()) {
            if (resultSet.next() && resultSet.getLong(1) > 0) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO bad_case_record (
                    case_code, source_type, stage, badcase_type, severity, project_name, requirement_title,
                    input_prompt, generated_prd, generated_code, expected_behavior, failure_reason,
                    reviewed_by, tags
                ) VALUES (?, 'SEED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'cold-start', ?)
                """)) {
            int index = 1;
            for (String[] type : PRD_TYPES) {
                for (String scenario : PRD_SCENARIOS) {
                    addSeed(insert, index++, "PRD", type, scenario);
                }
            }
            for (String[] type : CODE_TYPES) {
                for (String scenario : CODE_SCENARIOS) {
                    addSeed(insert, index++, "CODE", type, scenario);
                }
            }
            for (String[] type : E2E_TYPES) {
                for (String scenario : E2E_SCENARIOS) {
                    addSeed(insert, index++, "PRD_TO_CODE", type, scenario);
                }
            }
            insert.executeBatch();
        }
    }

    private void addSeed(PreparedStatement insert, int index, String stage, String[] type, String scenario) throws SQLException {
        String caseCode = "BC-COLD-" + String.format("%03d", index);
        String severity = index % 17 == 0 ? "P0" : (index % 3 == 0 ? "P2" : "P1");
        String title = scenario + " - " + type[0].toLowerCase().replace('_', ' ');
        String prompt = "Build " + scenario + " and generate " + ("CODE".equals(stage) ? "implementation code" : "a PRD") + ".";
        String prd = "Generated PRD excerpt: broad feature list for " + scenario + " with insufficient constraints around " + type[0] + ".";
        String code = "Generated code excerpt: placeholder implementation for " + scenario + " that demonstrates " + type[0] + ".";
        String expected = expectedFor(stage, type[0]);
        String failure = type[1] + " Scenario: " + scenario + ".";
        insert.setString(1, caseCode);
        insert.setString(2, stage);
        insert.setString(3, type[0]);
        insert.setString(4, severity);
        insert.setString(5, "Cold Start Badcase Library");
        insert.setString(6, title);
        insert.setString(7, prompt);
        insert.setString(8, "CODE".equals(stage) ? null : prd);
        insert.setString(9, "PRD".equals(stage) ? null : code);
        insert.setString(10, expected);
        insert.setString(11, failure);
        insert.setString(12, "cold-start," + stage.toLowerCase() + "," + type[0].toLowerCase());
        insert.addBatch();
    }

    private String expectedFor(String stage, String type) {
        if ("PRD".equals(stage)) {
            return "The PRD should state assumptions, ask for missing context when needed, define scope, roles, data, exceptions, and acceptance criteria.";
        }
        if ("CODE".equals(stage)) {
            return "The generated code should follow the existing stack, compile, enforce security and permissions, and include focused verification.";
        }
        return "The generated code should faithfully implement the reviewed PRD without drifting in naming, fields, permissions, exceptions, or metrics.";
    }
}
