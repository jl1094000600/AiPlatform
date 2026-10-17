-- Run after schema-tenant-security.sql, including for existing tenant roles.
INSERT IGNORE INTO sys_permission (permission_code, permission_name, resource_type) VALUES
    ('project-workspace:view', '项目工作台查看', 'API'),
    ('project-workspace:execute', '项目工作台执行', 'API'),
    ('project-workspace:manage', '项目指派管理', 'API');

INSERT IGNORE INTO sys_role_permission (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id FROM sys_role r JOIN sys_permission p
WHERE r.is_deleted = 0 AND (
    (r.role_code IN ('platform_admin', 'tenant_admin')
        AND p.permission_code IN ('project-workspace:view', 'project-workspace:execute', 'project-workspace:manage'))
    OR (r.role_code = 'developer'
        AND p.permission_code IN ('project-workspace:view', 'project-workspace:execute'))
    OR (r.role_code IN ('reviewer', 'readonly') AND p.permission_code = 'project-workspace:view'));

INSERT IGNORE INTO sys_menu (menu_code, menu_name, path, icon, permission_code, parent_id, sort_order)
VALUES ('group-automation', '自动化交付', NULL, 'Promotion', NULL, NULL, 20);
INSERT IGNORE INTO sys_menu (menu_code, menu_name, path, icon, permission_code, sort_order)
VALUES ('project-workspaces', '项目工作台', '/project-workspaces', 'OfficeBuilding', 'project-workspace:view', 15);
UPDATE sys_menu SET permission_code = 'project-workspace:view' WHERE menu_code = 'project-workspaces';
UPDATE sys_menu child JOIN sys_menu parent ON parent.menu_code = 'group-automation'
SET child.parent_id = parent.id, child.sort_order = 15 WHERE child.menu_code = 'project-workspaces';
