package com.aipal.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.aipal.security.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Set;

@Configuration
public class WebConfig implements MetaObjectHandler {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            private final Set<String> ignoredTables = Set.of(
                    "sys_tenant", "sys_user", "sys_user_tenant", "sys_role", "sys_permission",
                    "sys_user_role", "sys_role_permission", "sys_menu", "ai_output_governance_policy_template",
                    "agent_tool_definition"
            );

            @Override
            public Expression getTenantId() {
                return new LongValue(TenantContext.tenantId());
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return tableName != null && ignoredTables.contains(tableName.toLowerCase());
            }
        }));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        fillIfNull(metaObject, "createTime", LocalDateTime.now());
        fillIfNull(metaObject, "updateTime", LocalDateTime.now());
        if (metaObject.hasSetter("tenantId")) {
            fillIfNull(metaObject, "tenantId", TenantContext.tenantId());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        fillIfNull(metaObject, "updateTime", LocalDateTime.now());
    }

    private void fillIfNull(MetaObject metaObject, String fieldName, Object value) {
        if (metaObject.hasSetter(fieldName) && metaObject.getValue(fieldName) == null) {
            metaObject.setValue(fieldName, value);
        }
    }
}
