package com.aipal.service.memory;

import com.aipal.common.BizException;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.AiMemoryProjectMember;
import com.aipal.entity.SysUser;
import com.aipal.entity.SysUserTenant;
import com.aipal.mapper.AiMemoryProjectMapper;
import com.aipal.mapper.AiMemoryProjectMemberMapper;
import com.aipal.mapper.SysUserMapper;
import com.aipal.mapper.SysUserTenantMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MemoryProjectService {

    private final AiMemoryProjectMapper projectMapper;
    private final AiMemoryProjectMemberMapper memberMapper;
    private final SysUserMapper userMapper;
    private final MemoryTrustedProjectContext trustedProjectContext;
    private final SysUserTenantMapper userTenantMapper;

    @Transactional
    public AiMemoryProject create(String projectKey, String projectName, String projectType) {
        if (projectKey == null || projectKey.isBlank() || projectName == null || projectName.isBlank()) {
            throw new BizException(400, "项目键和项目名称不能为空");
        }
        if (TenantContext.userId() == null) throw new BizException(403, "系统任务不能创建项目");
        AiMemoryProject existing = projectMapper.selectOne(new LambdaQueryWrapper<AiMemoryProject>()
                .eq(AiMemoryProject::getTenantId, TenantContext.tenantId())
                .eq(AiMemoryProject::getProjectKey, projectKey.trim())
                .last("LIMIT 1"));
        if (existing != null) throw new BizException(409, "项目键已存在");
        AiMemoryProject project = new AiMemoryProject();
        project.setTenantId(TenantContext.tenantId());
        project.setProjectKey(projectKey.trim());
        project.setProjectName(projectName.trim());
        project.setProjectType(projectType == null || projectType.isBlank() ? "CUSTOMER_PROJECT" : projectType.trim());
        project.setOwnerUserId(TenantContext.userId());
        project.setStatus("ACTIVE");
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        project.setIsDeleted(0);
        projectMapper.insert(project);
        upsertMember(project, TenantContext.userId(), "OWNER");
        return project;
    }

    public List<AiMemoryProject> listAccessible() {
        if (TenantContext.hasPermission("memory:policy")) {
            return projectMapper.selectList(activeProjectQuery());
        }
        return listMemberProjects(TenantContext.userId());
    }

    private List<AiMemoryProject> listMemberProjects(Long userId) {
        if (userId == null) return List.of();
        List<Long> projectIds = activeMemberProjectIds(userId);
        if (projectIds.isEmpty()) return List.of();
        return projectMapper.selectList(activeProjectQuery().in(AiMemoryProject::getId, projectIds));
    }

    private List<Long> activeMemberProjectIds(Long userId) {
        return memberMapper.selectList(new LambdaQueryWrapper<AiMemoryProjectMember>()
                        .eq(AiMemoryProjectMember::getTenantId, TenantContext.tenantId())
                        .eq(AiMemoryProjectMember::getUserId, userId)
                        .eq(AiMemoryProjectMember::getStatus, "ACTIVE"))
                .stream().map(AiMemoryProjectMember::getProjectId).toList();
    }

    @Transactional
    public AiMemoryProject ensureWorkspaceForPipeline(String projectKey, String projectName) {
        if (projectKey == null || projectKey.isBlank() || projectName == null || projectName.isBlank()) {
            throw new BizException(400, "项目键和项目名称不能为空");
        }
        AiMemoryProject existing = projectMapper.selectOne(activeProjectQuery()
                .eq(AiMemoryProject::getProjectKey, projectKey.trim())
                .last("LIMIT 1"));
        if (existing != null) {
            return requireAccessibleProject(projectKey);
        }
        return create(projectKey, projectName, "DELIVERY_PROJECT");
    }

    @Transactional
    public AiMemoryProjectMember addMember(String projectKey, Long userId, String role) {
        AiMemoryProject project = requireManageableProject(projectKey);
        if (userId == null) throw new BizException(400, "成员用户不能为空");
        return upsertMember(project, userId, role == null || role.isBlank() ? "MEMBER" : role.trim());
    }

    public MemoryTrustedProjectContext.ProjectScope openAccessibleProject(String projectKey) {
        return trustedProjectContext.openProject(requireAccessibleProject(projectKey));
    }

    public AiMemoryProject requireAccessibleProject(String projectKey) {
        AiMemoryProject project = requireActiveProject(projectKey);
        if (TenantContext.hasPermission("memory:policy")) return project;
        Long userId = TenantContext.userId();
        if (userId == null) throw new BizException(403, "无权访问项目记忆");
        Long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<AiMemoryProjectMember>()
                .eq(AiMemoryProjectMember::getTenantId, TenantContext.tenantId())
                .eq(AiMemoryProjectMember::getProjectId, project.getId())
                .eq(AiMemoryProjectMember::getUserId, userId)
                .eq(AiMemoryProjectMember::getStatus, "ACTIVE"));
        if (memberCount == null || memberCount == 0) throw new BizException(403, "不是该项目成员");
        return project;
    }

    public List<ProjectMemberView> listMentionableUsers(String projectKey) {
        return toMemberViews(requireAccessibleProject(projectKey), false);
    }

    /** Workspace authorization is independent from memory administration. */
    public List<AiMemoryProject> listAccessibleWorkspaces() {
        requireWorkspacePermission("project-workspace:view");
        if (!isActiveTenantUser(TenantContext.userId())) return List.of();
        return listMemberProjects(TenantContext.userId());
    }

    public AiMemoryProject requireAccessibleWorkspaceProject(String projectKey) {
        requireWorkspacePermission("project-workspace:view");
        AiMemoryProject project = requireActiveProject(projectKey);
        if (!isActiveTenantUser(TenantContext.userId()) || activeMember(project, TenantContext.userId()) == null) {
            throw new BizException(403, "不是该项目的有效成员");
        }
        return project;
    }

    public boolean canManageWorkspaceProject(String projectKey) {
        AiMemoryProject project = requireAccessibleWorkspaceProject(projectKey);
        if (TenantContext.platformAdmin() || TenantContext.hasPermission("project-workspace:manage")) return true;
        AiMemoryProjectMember member = activeMember(project, TenantContext.userId());
        // A project owner ID grants management only after current active membership is verified.
        return member != null && (Objects.equals(TenantContext.userId(), project.getOwnerUserId())
                || isOwnerOrManager(member));
    }

    public ProjectMemberView requireActiveMember(String projectKey, Long userId) {
        AiMemoryProject project = requireAccessibleWorkspaceProject(projectKey);
        AiMemoryProjectMember member = activeMember(project, userId);
        if (member == null || !isActiveTenantUser(userId)) {
            throw new BizException(400, "被指派人不是当前项目的有效成员");
        }
        SysUser user = userMapper.selectById(userId);
        return new ProjectMemberView(userId, user.getUsername(), user.getRealName(), member.getMemberRole());
    }

    public List<ProjectMemberView> listWorkspaceMentionableUsers(String projectKey) {
        return toMemberViews(requireAccessibleWorkspaceProject(projectKey), true);
    }

    private List<ProjectMemberView> toMemberViews(AiMemoryProject project, boolean activeTenantUsersOnly) {
        return memberMapper.selectList(new LambdaQueryWrapper<AiMemoryProjectMember>()
                        .eq(AiMemoryProjectMember::getTenantId, TenantContext.tenantId())
                        .eq(AiMemoryProjectMember::getProjectId, project.getId())
                        .eq(AiMemoryProjectMember::getStatus, "ACTIVE")
                        .orderByAsc(AiMemoryProjectMember::getId))
                .stream()
                .filter(member -> !activeTenantUsersOnly || isActiveTenantUser(member.getUserId()))
                .map(member -> {
                    SysUser user = userMapper.selectById(member.getUserId());
                    return new ProjectMemberView(member.getUserId(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getRealName(),
                            member.getMemberRole());
                })
                .toList();
    }

    /** Used by personal work lists: administrative access cannot revive a removed assignee. */
    public boolean isActiveWorkspaceMember(String projectKey, Long userId) {
        AiMemoryProject project = projectMapper.selectOne(activeProjectQuery()
                .eq(AiMemoryProject::getProjectKey, projectKey).last("LIMIT 1"));
        return project != null && isActiveTenantUser(userId) && activeMember(project, userId) != null;
    }

    private AiMemoryProjectMember activeMember(AiMemoryProject project, Long userId) {
        if (userId == null) return null;
        return memberMapper.selectOne(new LambdaQueryWrapper<AiMemoryProjectMember>()
                .eq(AiMemoryProjectMember::getTenantId, TenantContext.tenantId())
                .eq(AiMemoryProjectMember::getProjectId, project.getId())
                .eq(AiMemoryProjectMember::getUserId, userId)
                .eq(AiMemoryProjectMember::getStatus, "ACTIVE").last("LIMIT 1"));
    }

    private boolean isActiveTenantUser(Long userId) {
        if (userId == null) return false;
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || Integer.valueOf(1).equals(user.getIsDeleted())) return false;
        Long count = userTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenant>()
                .eq(SysUserTenant::getTenantId, TenantContext.tenantId())
                .eq(SysUserTenant::getUserId, userId).eq(SysUserTenant::getStatus, 1));
        return count != null && count > 0;
    }

    public void requireWorkspacePermission(String permission) {
        if (TenantContext.userId() == null || !TenantContext.hasPermission(permission)) {
            throw new BizException(403, "无权限访问：" + permission);
        }
    }

    /** Project owner/manager check for operations that change shared project execution state. */
    public boolean canManageProject(String projectKey) {
        AiMemoryProject project = requireAccessibleProject(projectKey);
        if (TenantContext.hasPermission("memory:policy") || TenantContext.userId().equals(project.getOwnerUserId())) return true;
        return isOwnerOrManager(activeMember(project, TenantContext.userId()));
    }

    private boolean isOwnerOrManager(AiMemoryProjectMember member) {
        return member != null && ("OWNER".equals(member.getMemberRole()) || "MANAGER".equals(member.getMemberRole()));
    }

    private AiMemoryProject requireManageableProject(String projectKey) {
        AiMemoryProject project = requireAccessibleProject(projectKey);
        if (!canManageProject(projectKey)) {
            throw new BizException(403, "无权管理项目成员");
        }
        return project;
    }

    private AiMemoryProject requireActiveProject(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) throw new BizException(400, "项目键不能为空");
        AiMemoryProject project = projectMapper.selectOne(activeProjectQuery()
                .eq(AiMemoryProject::getProjectKey, projectKey.trim()).last("LIMIT 1"));
        if (project == null) throw new BizException(404, "项目不存在或已停用");
        return project;
    }

    private LambdaQueryWrapper<AiMemoryProject> activeProjectQuery() {
        return new LambdaQueryWrapper<AiMemoryProject>()
                .eq(AiMemoryProject::getTenantId, TenantContext.tenantId())
                .eq(AiMemoryProject::getStatus, "ACTIVE")
                .orderByDesc(AiMemoryProject::getUpdateTime);
    }

    private AiMemoryProjectMember upsertMember(AiMemoryProject project, Long userId, String role) {
        AiMemoryProjectMember member = memberMapper.selectOne(new LambdaQueryWrapper<AiMemoryProjectMember>()
                .eq(AiMemoryProjectMember::getTenantId, TenantContext.tenantId())
                .eq(AiMemoryProjectMember::getProjectId, project.getId())
                .eq(AiMemoryProjectMember::getUserId, userId).last("LIMIT 1"));
        if (member == null) {
            member = new AiMemoryProjectMember();
            member.setTenantId(project.getTenantId());
            member.setProjectId(project.getId());
            member.setUserId(userId);
            member.setCreateTime(LocalDateTime.now());
            member.setIsDeleted(0);
            memberMapper.insert(member);
        }
        member.setMemberRole(role);
        member.setStatus("ACTIVE");
        member.setUpdateTime(LocalDateTime.now());
        memberMapper.updateById(member);
        return member;
    }

    public record ProjectMemberView(Long userId, String username, String realName, String memberRole) {
    }
}
