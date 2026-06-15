package com.aipal.config;

import com.aipal.security.TenantContext;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebConfigTenantFillTest {

    private final WebConfig webConfig = new WebConfig();

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void globalEntityWithoutTenantFieldDoesNotRequireTenantContext() {
        MetaObject metaObject = SystemMetaObject.forObject(new GlobalEntity());

        assertDoesNotThrow(() -> webConfig.insertFill(metaObject));
    }

    @Test
    void tenantEntityRequiresTenantContext() {
        MetaObject metaObject = SystemMetaObject.forObject(new TenantEntity());

        assertThrows(IllegalStateException.class, () -> webConfig.insertFill(metaObject));
    }

    static class GlobalEntity {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    static class TenantEntity {
        private Long tenantId;

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }
    }
}
