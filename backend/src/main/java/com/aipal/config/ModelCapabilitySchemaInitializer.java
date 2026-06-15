package com.aipal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
@DependsOn("tenantSecuritySchemaInitializer")
@ConditionalOnProperty(name = "aipal.schema-initialization.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ModelCapabilitySchemaInitializer {
    private static final String TABLE_NAME = "ai_model";
    private static final String INDEX_NAME = "idx_model_capability_default";

    private final DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (!tableExists(connection, TABLE_NAME)) {
                return;
            }
            addColumnIfMissing(connection, statement, "capability_type",
                    "ALTER TABLE ai_model ADD COLUMN capability_type VARCHAR(16) NOT NULL DEFAULT 'CHAT'");
            addColumnIfMissing(connection, statement, "default_for_capability",
                    "ALTER TABLE ai_model ADD COLUMN default_for_capability TINYINT NOT NULL DEFAULT 0");
            statement.executeUpdate("UPDATE ai_model SET capability_type = 'CHAT' "
                    + "WHERE capability_type IS NULL OR capability_type = ''");
            addIndexIfMissing(connection, statement);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize model capability schema: " + e.getMessage(), e);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null,
                tableName.toUpperCase(), new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String columnName,
                                    String alterSql) throws SQLException {
        if (!columnExists(connection, columnName)) {
            statement.executeUpdate(alterSql);
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, TABLE_NAME, columnName)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null,
                TABLE_NAME.toUpperCase(), columnName.toUpperCase())) {
            return columns.next();
        }
    }

    private void addIndexIfMissing(Connection connection, Statement statement) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, TABLE_NAME, false, false)) {
            while (indexes.next()) {
                if (INDEX_NAME.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        statement.executeUpdate("CREATE INDEX " + INDEX_NAME
                + " ON ai_model (tenant_id, capability_type, default_for_capability, status)");
    }
}
