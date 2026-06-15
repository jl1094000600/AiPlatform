package com.aipal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@ConditionalOnProperty(name = "aipal.schema-initialization.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class UserMemorySchemaInitializer {

    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ai_user_memory (
                        id                 BIGINT       NOT NULL AUTO_INCREMENT,
                        memory_code        VARCHAR(64)  NOT NULL,
                        user_key           VARCHAR(128) NOT NULL,
                        user_id            BIGINT       DEFAULT NULL,
                        username           VARCHAR(64)  DEFAULT NULL,
                        source_type        VARCHAR(32)  NOT NULL DEFAULT 'PIPELINE',
                        source_id          BIGINT       DEFAULT NULL,
                        summary_content    MEDIUMTEXT   NOT NULL,
                        raw_count          INT          NOT NULL DEFAULT 0,
                        compression_model  VARCHAR(128) DEFAULT NULL,
                        memory_start_time  DATETIME     DEFAULT NULL,
                        memory_end_time    DATETIME     DEFAULT NULL,
                        create_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
                        update_time        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted         TINYINT      NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_memory_code (memory_code),
                        KEY idx_memory_user_time (user_key, create_time),
                        KEY idx_memory_source (source_type, source_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
            addColumnIfMissing(connection, statement, "automation_pipeline", "initiator_user_id",
                    "ALTER TABLE automation_pipeline ADD COLUMN initiator_user_id BIGINT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "automation_pipeline", "initiator_username",
                    "ALTER TABLE automation_pipeline ADD COLUMN initiator_username VARCHAR(64) DEFAULT NULL");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize user memory schema: " + e.getMessage(), e);
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
