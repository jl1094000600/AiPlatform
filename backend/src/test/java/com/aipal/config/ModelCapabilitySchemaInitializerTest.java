package com.aipal.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCapabilitySchemaInitializerTest {

    @Test
    void addsCapabilityColumnsAndIndexIdempotently() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:model-capability;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE ai_model (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        status TINYINT NOT NULL DEFAULT 1
                    )
                    """);
        }
        ModelCapabilitySchemaInitializer initializer = new ModelCapabilitySchemaInitializer(dataSource);

        initializer.initialize();
        initializer.initialize();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertTrue(hasColumn(metadata, "CAPABILITY_TYPE"));
            assertTrue(hasColumn(metadata, "DEFAULT_FOR_CAPABILITY"));
            assertTrue(hasIndex(metadata, "IDX_MODEL_CAPABILITY_DEFAULT"));
        }
    }

    private boolean hasColumn(DatabaseMetaData metadata, String columnName) throws Exception {
        try (ResultSet columns = metadata.getColumns(null, null, "AI_MODEL", columnName)) {
            return columns.next();
        }
    }

    private boolean hasIndex(DatabaseMetaData metadata, String indexName) throws Exception {
        try (ResultSet indexes = metadata.getIndexInfo(null, null, "AI_MODEL", false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
