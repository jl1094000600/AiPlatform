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
public class SkillSchemaInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ai_skill (
                        id                   BIGINT       NOT NULL AUTO_INCREMENT,
                        skill_code           VARCHAR(64)  NOT NULL,
                        skill_name           VARCHAR(128) NOT NULL,
                        description          TEXT         DEFAULT NULL,
                        status               TINYINT      NOT NULL DEFAULT 1,
                        prompt_content       MEDIUMTEXT   DEFAULT NULL,
                        function_definitions JSON         DEFAULT NULL,
                        create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP,
                        update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted           TINYINT      NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_skill_code (skill_code),
                        KEY idx_skill_status_time (status, create_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            addColumnIfMissing(connection, statement, "automation_pipeline", "skill_id",
                    "ALTER TABLE automation_pipeline ADD COLUMN skill_id BIGINT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "automation_pipeline", "skill_snapshot",
                    "ALTER TABLE automation_pipeline ADD COLUMN skill_snapshot MEDIUMTEXT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "automation_generation_job", "input_tokens",
                    "ALTER TABLE automation_generation_job ADD COLUMN input_tokens INT DEFAULT 0");
            addColumnIfMissing(connection, statement, "automation_generation_job", "output_tokens",
                    "ALTER TABLE automation_generation_job ADD COLUMN output_tokens INT DEFAULT 0");
            addColumnIfMissing(connection, statement, "automation_generation_job", "total_tokens",
                    "ALTER TABLE automation_generation_job ADD COLUMN total_tokens INT DEFAULT 0");
            addColumnIfMissing(connection, statement, "mon_call_record", "user_id",
                    "ALTER TABLE mon_call_record ADD COLUMN user_id BIGINT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "mon_call_record", "username",
                    "ALTER TABLE mon_call_record ADD COLUMN username VARCHAR(64) DEFAULT NULL");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize skill schema: " + e.getMessage(), e);
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
