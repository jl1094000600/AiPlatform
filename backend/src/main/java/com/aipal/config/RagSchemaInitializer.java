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
public class RagSchemaInitializer {
    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            addColumnIfMissing(connection, statement, "rag_ingestion_record", "chunk_mode",
                    "ALTER TABLE rag_ingestion_record ADD COLUMN chunk_mode VARCHAR(32) DEFAULT 'FIXED'");
            addColumnIfMissing(connection, statement, "rag_ingestion_record", "content_type",
                    "ALTER TABLE rag_ingestion_record ADD COLUMN content_type VARCHAR(32) DEFAULT 'AUTO'");
            addColumnIfMissing(connection, statement, "rag_ingestion_record", "semantic_model_id",
                    "ALTER TABLE rag_ingestion_record ADD COLUMN semantic_model_id BIGINT DEFAULT NULL");
            addColumnIfMissing(connection, statement, "rag_ingestion_record", "semantic_model_code",
                    "ALTER TABLE rag_ingestion_record ADD COLUMN semantic_model_code VARCHAR(128) DEFAULT NULL");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize RAG schema: " + e.getMessage(), e);
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
