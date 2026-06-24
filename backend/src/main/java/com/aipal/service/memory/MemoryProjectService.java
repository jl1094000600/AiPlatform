package com.aipal.service.memory;

import com.aipal.common.BizException;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.AiMemoryProjectMember;
import com.aipal.mapper.AiMemoryProjectMapper;
import com.aipal.mapper.AiMemoryProjectMemberMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryProjectService {

    private final AiMemoryProjectMapper projectMapper;
    private final AiMemoryProjectMemberMapper memberMapper;
    private final MemoryTrustedProjectContext trustedProjectContext;

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
        Long userId = TenantContext.userId();
        if (TenantContext.hasPermission("memory:policy")) {
            return projectMapper.selectList(activeProjectQuery());
        }
        if (userId == null) return List.of();
        List<Long> projectIds = memberMapper.selectList(new LambdaQueryWrapper<AiMemoryProjectMember>()
                        .eq(AiMemoryProjectMember::getTenantId, TenantContext.tenantId())
                        .eq(AiMemoryProjectMember::getUserId, userId)
                        .eq(AiMemoryProjectMember::getStatus, "ACTIVE"))
                .stream().map(AiMemoryProjectMember::getProjectId).toList();
        if (projectIds.isEmpty()) return List.of();
        return projectMapper.selectList(activeProjectQuery().in(AiMemoryProject::getId, projectIds));
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

    /** Project owner/manager check for operations that change shared project execution state. */
    public boolean canManageProject(String projectKey) {
        AiMemoryProject project = requireAccessibleProject(projectKey);
        if (TenantContext.hasPermission("memory:policy") || TenantContext.userId().equals(project.getOwnerUserId())) return true;
        AiMemoryProjectMember member = memberMapper.selectOne(new LambdaQueryWrapper<AiMemoryProjectMember>()
                .eq(AiMemoryProjectMember::getProjectId, project.getId())
                .eq(AiMemoryProjectMember::getUserId, TenantContext.userId())
                .eq(AiMemoryProjectMember::getStatus, "ACTIVE").last("LIMIT 1"));
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
}
