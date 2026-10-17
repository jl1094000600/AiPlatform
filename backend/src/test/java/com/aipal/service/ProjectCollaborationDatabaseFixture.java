package com.aipal.service;

import com.aipal.config.ProjectCollaborationSchemaInitializer;
import com.aipal.entity.*;
import com.aipal.mapper.*;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryProjectService;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/** Real MyBatis and Spring transactions, without loading unrelated application services. */
final class ProjectCollaborationDatabaseFixture {
    final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:collaboration-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
    final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
    final SqlSessionTemplate sql;
    final MemoryProjectService memory;
    final ProjectAssignmentService assignments;

    ProjectCollaborationDatabaseFixture() throws Exception {
        new ProjectCollaborationSchemaInitializer(dataSource).initialize();
        for (Class<?> entity : new Class<?>[] { AiMemoryProject.class, AiMemoryProjectMember.class,
                SysUser.class, SysUserTenant.class, AgentRun.class, AutomationApproval.class,
                AutomationPipeline.class, SysAuditLog.class }) createAncillaryTable(entity);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapper : new Class<?>[] { AiMemoryProjectMapper.class, AiMemoryProjectMemberMapper.class,
                SysUserMapper.class, SysUserTenantMapper.class, AgentRunMapper.class,
                AutomationApprovalMapper.class, AutomationPipelineMapper.class, ProjectTaskDraftMapper.class,
                ProjectAssignmentMapper.class, ProjectAssignmentEventMapper.class, SysAuditLogMapper.class }) {
            configuration.addMapper(mapper);
        }
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        SqlSessionFactory sessionFactory = factory.getObject();
        sql = new SqlSessionTemplate(sessionFactory);
        memory = new MemoryProjectService(mapper(AiMemoryProjectMapper.class), mapper(AiMemoryProjectMemberMapper.class),
                mapper(SysUserMapper.class), null, mapper(SysUserTenantMapper.class));
        assignments = transactional(new ProjectAssignmentService(memory, mapper(ProjectAssignmentMapper.class),
                mapper(ProjectAssignmentEventMapper.class), mapper(AgentRunMapper.class),
                mapper(AutomationApprovalMapper.class), mapper(AutomationPipelineMapper.class),
                mapper(ProjectTaskDraftMapper.class), mapper(SysAuditLogMapper.class)));
        seedUser(1, 1); seedUser(2, 1); seedUser(3, 1);
        seedProject(1, "project-a", 1, 1);
        seedMember(1, 1, 1, "OWNER"); seedMember(1, 1, 2, "MEMBER"); seedMember(1, 1, 3, "MEMBER");
        context(1, 1, false, "project-workspace:view", "project-workspace:execute");
    }

    <T> T mapper(Class<T> type) { return sql.getMapper(type); }

    @SuppressWarnings("unchecked")
    <T> T transactional(T target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    void seedUser(long userId, long tenantId) {
        jdbc.update("INSERT INTO sys_user (id, username, real_name, status, is_deleted, default_tenant_id) VALUES (?, ?, ?, 1, 0, 99)",
                userId, "user-" + userId, "Member " + userId);
        jdbc.update("INSERT INTO sys_user_tenant (user_id, tenant_id, status) VALUES (?, ?, 1)", userId, tenantId);
    }

    void seedProject(long id, String key, long tenantId, long owner) {
        jdbc.update("INSERT INTO ai_memory_project (id, tenant_id, project_key, project_name, owner_user_id, status, is_deleted) VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)",
                id, tenantId, key, key, owner);
    }

    void seedMember(long tenantId, long projectId, long userId, String role) {
        jdbc.update("INSERT INTO ai_memory_project_member (tenant_id, project_id, user_id, member_role, status, is_deleted) VALUES (?, ?, ?, ?, 'ACTIVE', 0)",
                tenantId, projectId, userId, role);
    }

    void seedRun(long id, long tenantId, String projectKey) {
        jdbc.update("INSERT INTO agent_run (id, tenant_id, project_key, status, is_deleted, input_json, result_json) VALUES (?, ?, ?, 'QUEUED', 0, ?, ?)",
                id, tenantId, projectKey, "private prompt /Users/secret/input", "private output stack trace");
    }

    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    static void context(long userId, long tenantId, boolean platformAdmin, String... permissions) {
        TenantContext.set(new TenantContext.Context(userId, "user-" + userId, tenantId, "tenant-" + tenantId,
                platformAdmin, Set.of(), Set.of(permissions)));
    }

    private void createAncillaryTable(Class<?> entity) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(entity.getAnnotation(TableName.class).value()).append(" (");
        boolean first = true;
        for (Field field : entity.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!first) ddl.append(',');
            first = false;
            String column = field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
            ddl.append(column).append(' ');
            if ("id".equals(column)) ddl.append("BIGINT AUTO_INCREMENT PRIMARY KEY");
            else if (field.getType() == Long.class) ddl.append("BIGINT");
            else if (field.getType() == Integer.class) ddl.append("INT");
            else if (field.getType() == LocalDateTime.class) ddl.append("TIMESTAMP");
            else ddl.append("VARCHAR(10000)");
            if ("is_deleted".equals(column)) ddl.append(" DEFAULT 0");
        }
        jdbc.execute(ddl.append(')').toString());
    }
}
