-- Workflow canvas menu and permissions.
-- Runtime seeds also live in TenantSecuritySchemaInitializer; this script covers SQL-only bootstrap/upgrade.
INSERT IGNORE INTO sys_permission (permission_code, permission_name, resource_type) VALUES
    ('workflow:manage', '工作流管理', 'API'),
    ('workflow:publish', '工作流发布', 'API');

INSERT IGNORE INTO sys_menu (menu_code, menu_name, path, icon, permission_code, sort_order)
VALUES ('workflows', '工作流编排', '/workflows', 'Connection', 'workflow:manage', 18);
UPDATE sys_menu SET permission_code = 'workflow:manage' WHERE menu_code = 'workflows';

UPDATE sys_menu SET parent_id = (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_code = 'group-automation') parent), sort_order = 18
WHERE menu_code = 'workflows';

INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.is_deleted = 0
  AND ((r.role_code = 'developer' AND p.permission_code IN ('workflow:manage', 'workflow:publish'))
    OR (r.role_code IN ('platform_admin', 'tenant_admin')
        AND p.permission_code IN ('workflow:manage', 'workflow:publish')));
