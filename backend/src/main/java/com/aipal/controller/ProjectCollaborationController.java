package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.ProjectCollaborationDtos;
import com.aipal.security.RequirePermission;
import com.aipal.service.ProjectAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/project-workspaces")
@RequiredArgsConstructor
public class ProjectCollaborationController {
    private final ProjectAssignmentService assignmentService;

    @GetMapping("/{projectKey}/assignments")
    @RequirePermission("project-workspace:view")
    public Result<List<ProjectCollaborationDtos.AssignmentView>> list(@PathVariable String projectKey) {
        return Result.success(assignmentService.listProject(projectKey));
    }

    @GetMapping("/my-assignments")
    @RequirePermission("project-workspace:view")
    public Result<List<ProjectCollaborationDtos.AssignmentView>> mine() {
        return Result.success(assignmentService.listMine());
    }

    // Management is an OR of project ownership, project manager role and global manage permission.
    @PostMapping("/{projectKey}/assignments")
    @RequirePermission("project-workspace:view")
    public Result<ProjectCollaborationDtos.AssignmentView> assign(@PathVariable String projectKey,
                                                                @RequestBody ProjectCollaborationDtos.AssignRequest request) {
        return Result.success(assignmentService.assign(projectKey, request));
    }

    @PostMapping("/{projectKey}/assignments/{id}/reassign")
    @RequirePermission("project-workspace:view")
    public Result<ProjectCollaborationDtos.AssignmentView> reassign(@PathVariable String projectKey, @PathVariable Long id,
                                                                  @RequestBody ProjectCollaborationDtos.ReassignRequest request) {
        return Result.success(assignmentService.reassign(projectKey, id, request));
    }

    @PostMapping("/{projectKey}/assignments/{id}/accept")
    @RequirePermission("project-workspace:execute")
    public Result<ProjectCollaborationDtos.AssignmentView> accept(@PathVariable String projectKey, @PathVariable Long id,
                                                                @RequestBody ProjectCollaborationDtos.AssignmentActionRequest request) {
        return Result.success(assignmentService.accept(projectKey, id, request));
    }

    @PostMapping("/{projectKey}/assignments/{id}/complete")
    @RequirePermission("project-workspace:execute")
    public Result<ProjectCollaborationDtos.AssignmentView> complete(@PathVariable String projectKey, @PathVariable Long id,
                                                                  @RequestBody ProjectCollaborationDtos.AssignmentActionRequest request) {
        return Result.success(assignmentService.complete(projectKey, id, request));
    }
}
