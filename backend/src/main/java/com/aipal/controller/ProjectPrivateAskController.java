package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.ProjectPrivateAskDtos;
import com.aipal.dto.ProjectTaskDraftDtos;
import com.aipal.security.RequirePermission;
import com.aipal.service.ProjectPrivateAskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M3-C Private Ask API. Kept in its own controller so the zero-persistence question endpoint can
 * never share Shared Discussion or task draft write routes.
 */
@RestController
@RequestMapping("/api/v1/project-workspaces")
@RequiredArgsConstructor
public class ProjectPrivateAskController {

    private final ProjectPrivateAskService projectPrivateAskService;

    @PostMapping("/{projectKey}/private-ask")
    @RequirePermission("project-workspace:view")
    public Result<ProjectPrivateAskDtos.PrivateAskResponse> ask(
            @PathVariable String projectKey,
            @RequestBody ProjectPrivateAskDtos.PrivateAskRequest request) {
        return Result.success(projectPrivateAskService.ask(projectKey, request));
    }

    @PostMapping("/{projectKey}/private-ask/task-drafts")
    @RequirePermission("project-workspace:execute")
    public Result<ProjectTaskDraftDtos.TaskDraftView> convertToDraft(
            @PathVariable String projectKey,
            @RequestBody ProjectPrivateAskDtos.ConvertPrivateAskRequest request) {
        return Result.success(projectPrivateAskService.convertToDraft(projectKey, request));
    }
}
