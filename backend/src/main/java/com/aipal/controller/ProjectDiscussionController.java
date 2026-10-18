package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.ProjectDiscussionDtos.CoordinatorRequest;
import com.aipal.dto.ProjectDiscussionDtos.CoordinatorResponse;
import com.aipal.dto.ProjectDiscussionDtos.DiscussionMessageView;
import com.aipal.dto.ProjectDiscussionDtos.PostDiscussionRequest;
import com.aipal.security.RequirePermission;
import com.aipal.service.ProjectDiscussionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M3-B shared discussion API. Reading/posting needs project view permission; the read-only
 * Coordinator needs project execute permission.
 */
@RestController
@RequestMapping("/api/v1/project-workspaces")
@RequiredArgsConstructor
public class ProjectDiscussionController {

    private final ProjectDiscussionService projectDiscussionService;

    @GetMapping("/{projectKey}/discussions")
    @RequirePermission("project-workspace:view")
    public Result<List<DiscussionMessageView>> list(
            @PathVariable String projectKey,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return Result.success(projectDiscussionService.list(projectKey, limit));
    }

    @PostMapping("/{projectKey}/discussions")
    @RequirePermission("project-workspace:view")
    public Result<DiscussionMessageView> post(
            @PathVariable String projectKey,
            @RequestBody PostDiscussionRequest request) {
        return Result.success(projectDiscussionService.post(projectKey, request));
    }

    @PostMapping("/{projectKey}/discussion-coordinator")
    @RequirePermission("project-workspace:execute")
    public Result<CoordinatorResponse> coordinate(
            @PathVariable String projectKey,
            @RequestBody CoordinatorRequest request) {
        return Result.success(projectDiscussionService.coordinate(projectKey, request));
    }
}
