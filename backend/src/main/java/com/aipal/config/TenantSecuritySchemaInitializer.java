package com.aipal.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Component
@ConditionalOnProperty(name = "aipal.schema-initialization.enabled", havingValue = "true", matchIfMissing = true)
public class TenantSecuritySchemaInitializer {
    private static final String DEFAULT_TENANT_CODE = "aiplatform";
    private static final String DEFAULT_TENANT_NAME = "AIPlatform";
    private final DataSource dataSource;
    private final String bootstrapAdminPasswordHash;

    public TenantSecuritySchemaInitializer(
            DataSource dataSource,
            @Value("${security.bootstrap-admin-password-hash}") String bootstrapAdminPasswordHash) {
        if (bootstrapAdminPasswordHash == null || !bootstrapAdminPasswordHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(
                    "security.bootstrap-admin-password-hash must be an explicit 64-character SHA-256 hash");
        }
        this.dataSource = dataSource;
        this.bootstrapAdminPasswordHash = bootstrapAdminPasswordHash.toLowerCase(Locale.ROOT);
    }

    @PostConstruct
    public void initialize() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            createTables(statement);
            upgradeSystemTables(connection, statement);
            upgradeBusinessTables(connection, statement);
            upgradeTenantUniqueIndexes(connection, statement);
            seedDefaultTenant(statement);
            seedPermissions(statement);
            seedMenus(statement);
            seedRoles(statement);
            migrateExistingUsers(statement);
            migrateBusinessData(connection, statement);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize tenant security schema: " + e.getMessage(), e);
        }
    }

    private void createTables(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sys_tenant (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    tenant_code VARCHAR(64) NOT NULL,
                    tenant_name VARCHAR(128) NOT NULL,
                    contact_name VARCHAR(128) DEFAULT NULL,
                    contact_email VARCHAR(128) DEFAULT NULL,
                    status TINYINT NOT NULL DEFAULT 1,
                    expire_time DATETIME DEFAULT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_tenant_code (tenant_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sys_user_tenant (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    tenant_role VARCHAR(64) DEFAULT 'member',
                    default_tenant TINYINT NOT NULL DEFAULT 0,
                    status TINYINT NOT NULL DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_user_tenant (user_id, tenant_id),
                    KEY idx_tenant_id (tenant_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sys_user_role (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    tenant_id BIGINT NOT NULL DEFAULT 1,
                    user_id BIGINT NOT NULL,
                    role_id BIGINT NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_user_role (tenant_id, user_id, role_id),
                    KEY idx_role_id (role_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sys_role_permission (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    tenant_id BIGINT NOT NULL DEFAULT 1,
                    role_id BIGINT NOT NULL,
                    permission_id BIGINT NOT NULL,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_role_permission (tenant_id, role_id, permission_id),
                    KEY idx_permission_id (permission_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sys_menu (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    menu_code VARCHAR(64) NOT NULL,
                    menu_name VARCHAR(128) NOT NULL,
                    path VARCHAR(255) DEFAULT NULL,
                    icon VARCHAR(64) DEFAULT NULL,
                    permission_code VARCHAR(128) DEFAULT NULL,
                    parent_id BIGINT DEFAULT NULL,
                    sort_order INT NOT NULL DEFAULT 0,
                    visible TINYINT NOT NULL DEFAULT 1,
                    status TINYINT NOT NULL DEFAULT 1,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    is_deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_menu_code (menu_code),
                    KEY idx_permission_code (permission_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void upgradeSystemTables(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "sys_user", "default_tenant_id",
                "ALTER TABLE sys_user ADD COLUMN default_tenant_id BIGINT DEFAULT 1");
        addColumnIfMissing(connection, statement, "sys_user", "platform_admin",
                "ALTER TABLE sys_user ADD COLUMN platform_admin TINYINT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, statement, "sys_role", "tenant_id",
                "ALTER TABLE sys_role ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, statement, "sys_role", "role_scope",
                "ALTER TABLE sys_role ADD COLUMN role_scope VARCHAR(32) NOT NULL DEFAULT 'TENANT'");
        addColumnIfMissing(connection, statement, "sys_user_role", "tenant_id",
                "ALTER TABLE sys_user_role ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, statement, "sys_role_permission", "tenant_id",
                "ALTER TABLE sys_role_permission ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, statement, "sys_audit_log", "tenant_id",
                "ALTER TABLE sys_audit_log ADD COLUMN tenant_id BIGINT DEFAULT 1");
        addColumnIfMissing(connection, statement, "sys_audit_log", "permission_code",
                "ALTER TABLE sys_audit_log ADD COLUMN permission_code VARCHAR(128) DEFAULT NULL");
        addColumnIfMissing(connection, statement, "sys_audit_log", "request_path",
                "ALTER TABLE sys_audit_log ADD COLUMN request_path VARCHAR(255) DEFAULT NULL");
    }

    private void upgradeBusinessTables(Connection connection, Statement statement) throws SQLException {
        for (String table : tenantTables()) {
            if (tableExists(connection, table)) {
                addColumnIfMissing(connection, statement, table, "tenant_id",
                        "ALTER TABLE " + table + " ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1");
            }
        }
    }

    private List<String> tenantTables() {
        return List.of(
                "ai_a2a_task", "ai_agent", "ai_agent_graph_edge", "ai_agent_heartbeat",
                "ai_agent_quality_result", "ai_agent_quality_run", "ai_agent_registration",
                "ai_agent_registration_event", "ai_agent_runtime_config", "ai_agent_version",
                "ai_dataset", "ai_evaluation", "ai_evaluation_criteria", "ai_evaluation_sample", "ai_model",
                "ai_output_governance_record", "ai_skill", "ai_tts_config", "ai_tts_task",
                "ai_user_memory", "ai_workflow", "ai_workflow_execution", "alert_event",
                "alert_rule", "automation_approval", "automation_build_run", "automation_code_requirement_feedback", "automation_code_quality_issue",
                "automation_code_quality_evidence", "automation_code_quality_run", "automation_deploy_profile",
                "automation_deploy_run", "automation_generated_code_batch", "automation_generated_code_file",
                "automation_generation_job", "automation_pipeline",
                "automation_report_snapshot", "automation_stage_run", "automation_test_run", "billing_balance_transaction", "billing_budget",
                "billing_usage_daily", "biz_agent_auth", "biz_customer", "biz_module",
                "code_quality_rule", "code_quality_standard", "lowcode_invocation_record",
                "mon_api_metrics", "mon_call_record", "prompt_engineering_eval_result",
                "prompt_engineering_eval_run", "prompt_engineering_optimize_run",
                "prompt_engineering_prompt", "prompt_engineering_test_case",
                "prompt_engineering_version", "rag_ingestion_record", "sys_audit_log"
        );
    }

    private void upgradeTenantUniqueIndexes(Connection connection, Statement statement) throws SQLException {
        ensureTenantUniqueIndex(connection, statement, "ai_dataset", "uk_dataset_code",
                List.of("tenant_id", "dataset_code"));
        ensureTenantUniqueIndex(connection, statement, "ai_evaluation", "uk_evaluation_code",
                List.of("tenant_id", "evaluation_code"));
        ensureTenantUniqueIndex(connection, statement, "ai_evaluation_criteria", "uk_criteria_code",
                List.of("tenant_id", "criteria_code"));
        ensureTenantUniqueIndex(connection, statement, "ai_workflow", "uk_workflow_code",
                List.of("tenant_id", "workflow_code"));
        ensureTenantUniqueIndex(connection, statement, "ai_agent_registration", "uk_agent_instance",
                List.of("tenant_id", "agent_code", "instance_id"));
    }

    private void ensureTenantUniqueIndex(Connection connection, Statement statement, String tableName,
                                         String indexName, List<String> desiredColumns) throws SQLException {
        if (!tableExists(connection, tableName)) return;
        Map<Short, String> existingColumns = new TreeMap<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, tableName, true, false)) {
            while (indexes.next()) {
                String currentName = indexes.getString("INDEX_NAME");
                String columnName = indexes.getString("COLUMN_NAME");
                if (currentName != null && currentName.equalsIgnoreCase(indexName) && columnName != null) {
                    existingColumns.put(indexes.getShort("ORDINAL_POSITION"), columnName.toLowerCase(Locale.ROOT));
                }
            }
        }
        List<String> currentColumns = existingColumns.values().stream().toList();
        if (currentColumns.equals(desiredColumns)) return;
        if (!currentColumns.isEmpty()) {
            statement.executeUpdate("ALTER TABLE " + tableName + " DROP INDEX " + indexName);
        }
        statement.executeUpdate("ALTER TABLE " + tableName + " ADD UNIQUE KEY " + indexName
                + " (" + String.join(", ", desiredColumns) + ")");
    }

    private void seedDefaultTenant(Statement statement) throws SQLException {
        statement.executeUpdate("INSERT IGNORE INTO sys_tenant (id, tenant_code, tenant_name, status) VALUES (1, '" + DEFAULT_TENANT_CODE + "', '" + DEFAULT_TENANT_NAME + "', 1)");
        statement.executeUpdate("""
                UPDATE sys_tenant
                SET tenant_code = CASE
                        WHEN tenant_code = 'think_land'
                         AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_tenant WHERE tenant_code = 'aiplatform' AND id <> 1) existing_tenant)
                        THEN 'aiplatform'
                        ELSE tenant_code
                    END,
                    tenant_name = 'AIPlatform'
                WHERE id = 1
                  AND (tenant_code = 'think_land' OR tenant_name = 'Think Land')
                """);
        statement.executeUpdate("""
                INSERT IGNORE INTO sys_user (id, username, password, real_name, default_tenant_id, platform_admin, status)
                VALUES (1, 'admin', '%s', '系统管理员', 1, 1, 1)
                """.formatted(bootstrapAdminPasswordHash));
    }

    private void seedPermissions(Statement statement) throws SQLException {
        Object[][] permissions = {
                {"dashboard:view", "首页概况"}, {"agent:list", "Agent 查看"}, {"agent:create", "Agent 新建"},
                {"agent:update", "Agent 编辑"}, {"agent:delete", "Agent 删除"}, {"agent:invoke", "Agent 调用"},
                {"skill:list", "Skill 查看"}, {"skill:create", "Skill 新建"}, {"skill:update", "Skill 编辑"}, {"skill:delete", "Skill 删除"},
                {"memory:list", "记忆列表"}, {"memory:read", "记忆详情"}, {"memory:write", "记忆编辑"},
                {"memory:forget", "记忆遗忘"}, {"memory:policy", "记忆策略"}, {"memory:trace", "记忆调用追踪"},
                {"rag:list", "RAG 查看"}, {"rag:create", "RAG 新建"}, {"rag:update", "RAG 编辑"}, {"rag:delete", "RAG 删除"},
                {"model:list", "模型查看"}, {"model:create", "模型新建"}, {"model:update", "模型编辑"}, {"model:delete", "模型删除"},
                {"automation:list", "流水线查看"}, {"automation:create", "流水线新建"}, {"automation:run", "流水线运行"}, {"automation:approve", "流水线审批"}, {"automation:delete", "流水线删除"},
                {"code-quality:list", "代码质量查看"}, {"code-quality:manage", "代码质量管理"},
                {"governance:list", "AI 治理查看"}, {"governance:manage", "AI 治理管理"},
                {"prompt:list", "提示词查看"}, {"prompt:create", "提示词新建"}, {"prompt:update", "提示词编辑"}, {"prompt:evaluate", "提示词评测"}, {"prompt:publish", "提示词发布"},
                {"tenant:manage", "租户管理"}, {"member:manage", "成员管理"}, {"role:manage", "角色权限管理"}, {"menu:manage", "菜单权限管理"},
                {"audit:view", "审计查看"}, {"billing:view", "账单查看"}, {"alert:view", "告警查看"}, {"alert:manage", "告警管理"}, {"customer:manage", "客户管理"},
                {"workflow:manage", "工作流管理"}, {"benchmark:view", "基准测试查看"}, {"benchmark:run", "基准测试执行"},
                {"benchmark:manage", "基准测试标准管理"}, {"tts:invoke", "语音合成调用"}, {"tts:manage", "语音配置管理"},
                {"monitor:view", "监控查看"}, {"graph:manage", "调用图谱管理"}
        };
        for (Object[] permission : permissions) {
            statement.executeUpdate("INSERT IGNORE INTO sys_permission (permission_code, permission_name, resource_type) VALUES ('"
                    + permission[0] + "', '" + permission[1] + "', 'API')");
        }
    }

    private void seedMenus(Statement statement) throws SQLException {
        Object[][] groups = {
                {"group-workbench", "工作台", "DataAnalysis", 10},
                {"group-automation", "自动化交付", "Promotion", 20},
                {"group-agent", "Agent 能力", "Cpu", 30},
                {"group-operations", "运营观测", "Monitor", 40},
                {"group-system", "系统管理", "Menu", 90}
        };
        for (Object[] group : groups) {
            statement.executeUpdate("INSERT IGNORE INTO sys_menu (menu_code, menu_name, path, icon, permission_code, parent_id, sort_order) VALUES ('"
                    + group[0] + "', '" + group[1] + "', NULL, '" + group[2] + "', NULL, NULL, " + group[3] + ")");
        }
        Object[][] menus = {
                {"dashboard", "首页概况", "/dashboard", "DataAnalysis", "dashboard:view", 10, "group-workbench"},
                {"automation", "自动化流水线", "/automation", "Promotion", "automation:list", 10, "group-automation"},
                {"code-quality", "代码质量标准", "/code-quality", "Finished", "code-quality:list", 20, "group-automation"},
                {"ai-output-governance", "AI产出治理", "/ai-output-governance", "Connection", "governance:list", 30, "group-automation"},
                {"prompt-engineering", "提示词工程", "/prompt-engineering", "MagicStick", "prompt:list", 40, "group-automation"},
                {"deploy-profiles", "部署配置", "/deploy-profiles", "SetUp", "automation:list", 50, "group-automation"},
                {"skills", "Skill 管理", "/skills", "MagicStick", "skill:list", 10, "group-agent"},
                {"memories", "记忆管理", "/memories", "Tickets", "memory:list", 20, "group-agent"},
                {"agents", "Agent 管理", "/agents", "Cpu", "agent:list", 30, "group-agent"},
                {"agent-quality", "质量监控", "/agent-quality", "Finished", "agent:list", 40, "group-agent"},
                {"rag", "RAG 知识库", "/rag", "FolderOpened", "rag:list", 50, "group-agent"},
                {"models", "模型管理", "/models", "Box", "model:list", 60, "group-agent"},
                {"model-training", "模型训练", "/model-training", "DataLine", "model:update", 70, "group-agent"},
                {"platform-analytics", "平台分析", "/platform-analytics", "DataAnalysis", "dashboard:view", 10, "group-operations"},
                {"monitor", "接口监控", "/monitor", "Monitor", "monitor:view", 20, "group-operations"},
                {"graph", "调用图谱", "/graph", "Connection", "graph:manage", 30, "group-operations"},
                {"billing", "成本计费", "/billing", "Money", "billing:view", 40, "group-operations"},
                {"alerts", "告警中心", "/alerts", "Bell", "alert:view", 50, "group-operations"},
                {"audit-logs", "审计日志", "/audit-logs", "Tickets", "audit:view", 60, "group-operations"},
                {"customers", "客户管理", "/customers", "User", "customer:manage", 70, "group-operations"},
                {"invoke", "低代码调用", "/invoke", "MagicStick", "agent:invoke", 80, "group-operations"},
                {"tenants", "租户管理", "/tenants", "OfficeBuilding", "tenant:manage", 10, "group-system"},
                {"members", "成员管理", "/members", "UserFilled", "member:manage", 20, "group-system"},
                {"roles", "角色权限", "/roles", "Key", "role:manage", 30, "group-system"},
                {"menus", "菜单权限", "/menus", "Menu", "menu:manage", 40, "group-system"}
        };
        for (Object[] menu : menus) {
            statement.executeUpdate("INSERT IGNORE INTO sys_menu (menu_code, menu_name, path, icon, permission_code, sort_order) VALUES ('"
                    + menu[0] + "', '" + menu[1] + "', '" + menu[2] + "', '" + menu[3] + "', '" + menu[4] + "', " + menu[5] + ")");
            statement.executeUpdate("UPDATE sys_menu child JOIN sys_menu parent ON parent.menu_code = '" + menu[6]
                    + "' SET child.parent_id = parent.id, child.sort_order = " + menu[5]
                    + " WHERE child.menu_code = '" + menu[0] + "'");
        }
    }

    private void seedRoles(Statement statement) throws SQLException {
        Object[][] roles = {
                {"platform_admin", "平台超级管理员", "PLATFORM"},
                {"tenant_admin", "租户管理员", "TENANT"},
                {"developer", "开发者", "TENANT"},
                {"reviewer", "审核员", "TENANT"},
                {"readonly", "只读成员", "TENANT"}
        };
        for (Object[] role : roles) {
            statement.executeUpdate("INSERT IGNORE INTO sys_role (tenant_id, role_code, role_name, role_scope) VALUES (1, '"
                    + role[0] + "', '" + role[1] + "', '" + role[2] + "')");
        }
        statement.executeUpdate("""
                INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
                SELECT 1, r.id, p.id FROM sys_role r JOIN sys_permission p
                WHERE r.tenant_id = 1 AND r.role_code IN ('platform_admin', 'tenant_admin')
                """);
        statement.executeUpdate("""
                INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
                SELECT 1, r.id, p.id FROM sys_role r JOIN sys_permission p
                WHERE r.tenant_id = 1 AND r.role_code = 'developer'
                  AND p.permission_code IN ('dashboard:view','agent:list','agent:create','agent:update','agent:invoke','skill:list','skill:create','skill:update','memory:list','memory:read','memory:write','memory:forget','memory:trace','rag:list','rag:create','rag:update','model:list','automation:list','automation:create','automation:run','code-quality:list','governance:list','prompt:list','prompt:create','prompt:update','prompt:evaluate','graph:manage','monitor:view','benchmark:view','benchmark:run','tts:invoke')
                """);
        statement.executeUpdate("""
                INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
                SELECT 1, r.id, p.id FROM sys_role r JOIN sys_permission p
                WHERE r.tenant_id = 1 AND r.role_code = 'reviewer'
                  AND p.permission_code IN ('dashboard:view','automation:list','automation:approve','memory:list','memory:read','memory:trace','code-quality:list','governance:list','prompt:list','prompt:evaluate','audit:view','benchmark:view')
                """);
        statement.executeUpdate("""
                INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
                SELECT 1, r.id, p.id FROM sys_role r JOIN sys_permission p
                WHERE r.tenant_id = 1 AND r.role_code = 'readonly'
                  AND p.permission_code IN ('dashboard:view','agent:list','skill:list','memory:list','memory:read','rag:list','model:list','automation:list','code-quality:list','governance:list','prompt:list','billing:view','audit:view','benchmark:view')
                """);
    }

    private void migrateExistingUsers(Statement statement) throws SQLException {
        statement.executeUpdate("UPDATE sys_user SET default_tenant_id = 1 WHERE default_tenant_id IS NULL");
        statement.executeUpdate("""
                INSERT IGNORE INTO sys_user_tenant (user_id, tenant_id, tenant_role, default_tenant, status)
                SELECT id, 1, 'admin', 1, 1 FROM sys_user WHERE is_deleted = 0
                """);
        statement.executeUpdate("""
                INSERT IGNORE INTO sys_user_role (tenant_id, user_id, role_id)
                SELECT 1, u.id, r.id FROM sys_user u JOIN sys_role r
                WHERE r.tenant_id = 1 AND r.role_code = 'tenant_admin' AND u.is_deleted = 0
                """);
        statement.executeUpdate("""
                INSERT IGNORE INTO sys_user_role (tenant_id, user_id, role_id)
                SELECT 1, u.id, r.id FROM sys_user u JOIN sys_role r
                WHERE r.tenant_id = 1 AND r.role_code = 'platform_admin' AND u.platform_admin = 1
                """);
    }

    private void migrateBusinessData(Connection connection, Statement statement) throws SQLException {
        for (String table : tenantTables()) {
            if (tableExists(connection, table)) {
                statement.executeUpdate("UPDATE " + table + " SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0");
            }
        }
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String tableName,
                                    String columnName, String alterSql) throws SQLException {
        if (!tableExists(connection, tableName)) return;
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (!columns.next()) {
                statement.executeUpdate(alterSql);
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
