package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.ProjectTaskDraftDtos;
import com.aipal.security.RequirePermission;
import com.aipal.service.ProjectTaskDraftService;
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
public class ProjectTaskDraftController {

    private final ProjectTaskDraftService projectTaskDraftService;

    @GetMapping("/{projectKey}/task-drafts")
    @RequirePermission("project-workspace:view")
    public Result<List<ProjectTaskDraftDtos.TaskDraftView>> list(@PathVariable String projectKey) {
        return Result.success(projectTaskDraftService.list(projectKey));
    }

    @PostMapping("/{projectKey}/task-drafts")
    @RequirePermission("project-workspace:execute")
    public Result<ProjectTaskDraftDtos.TaskDraftView> create(
            @PathVariable String projectKey,
            @RequestBody ProjectTaskDraftDtos.CreateTaskDraftRequest request) {
        return Result.success(projectTaskDraftService.create(projectKey, request));
    }

    @PostMapping("/{projectKey}/task-drafts/{id}/confirm")
    @RequirePermission("project-workspace:execute")
    public Result<ProjectTaskDraftDtos.ConfirmTaskDraftResult> confirm(
            @PathVariable String projectKey,
            @PathVariable Long id,
            @RequestBody ProjectTaskDraftDtos.ConfirmTaskDraftRequest request) {
        return Result.success(projectTaskDraftService.confirm(projectKey, id, request));
    }

    @PostMapping("/{projectKey}/task-drafts/{id}/cancel")
    @RequirePermission("project-workspace:execute")
    public Result<ProjectTaskDraftDtos.TaskDraftView> cancel(
            @PathVariable String projectKey,
            @PathVariable Long id,
            @RequestBody ProjectTaskDraftDtos.CancelTaskDraftRequest request) {
        return Result.success(projectTaskDraftService.cancel(projectKey, id, request));
    }
}
