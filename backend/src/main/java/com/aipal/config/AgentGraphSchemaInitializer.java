package com.aipal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@RequiredArgsConstructor
public class AgentGraphSchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ai_agent_graph_edge (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        source_agent_id BIGINT NOT NULL,
                        source_agent_code VARCHAR(64) NOT NULL,
                        target_agent_id BIGINT NOT NULL,
                        target_agent_code VARCHAR(64) NOT NULL,
                        edge_type VARCHAR(32) NOT NULL DEFAULT 'ROUTE',
                        trigger_intent VARCHAR(64) DEFAULT NULL,
                        condition_expression VARCHAR(512) DEFAULT NULL,
                        param_mapping MEDIUMTEXT DEFAULT NULL,
                        timeout_seconds INT NOT NULL DEFAULT 30,
                        retry_count INT NOT NULL DEFAULT 0,
                        enabled TINYINT NOT NULL DEFAULT 1,
                        suitability_level VARCHAR(16) DEFAULT NULL,
                        suitability_score INT DEFAULT NULL,
                        suitability_message VARCHAR(512) DEFAULT NULL,
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        is_deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_agent_graph_edge_pair (source_agent_id, target_agent_id, edge_type, trigger_intent),
                        KEY idx_agent_graph_edge_source (source_agent_id, enabled),
                        KEY idx_agent_graph_edge_target (target_agent_id, enabled),
                        KEY idx_agent_graph_edge_codes (source_agent_code, target_agent_code)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize agent graph schema: " + e.getMessage(), e);
        }
    }
}
