package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.dto.ProjectWarRoomDtos;
import com.aipal.security.RequirePermission;
import com.aipal.service.ProjectWarRoomService;
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
public class ProjectWarRoomController {

    private final ProjectWarRoomService projectWarRoomService;

    @GetMapping
    @RequirePermission("automation:list")
    public Result<List<ProjectWarRoomDtos.ProjectWorkspaceView>> listWorkspaces() {
        return Result.success(projectWarRoomService.listWorkspaces());
    }

    @GetMapping("/{projectKey}/war-room")
    @RequirePermission("automation:list")
    public Result<ProjectWarRoomDtos.WarRoomResponse> getWarRoom(@PathVariable String projectKey) {
        return Result.success(projectWarRoomService.getWarRoom(projectKey));
    }

    @PostMapping("/{projectKey}/team-agent-runs")
    @RequirePermission("agent:invoke")
    public Result<ProjectWarRoomDtos.TeamAgentQueueRunView> createTeamAgentRun(
            @PathVariable String projectKey,
            @RequestBody ProjectWarRoomDtos.TeamAgentRunRequest request) {
        return Result.success(projectWarRoomService.createTeamAgentRun(projectKey, request));
    }

    @GetMapping("/{projectKey}/mentionable-users")
    @RequirePermission("automation:list")
    public Result<List<ProjectWarRoomDtos.MentionableUserView>> listMentionableUsers(@PathVariable String projectKey) {
        return Result.success(projectWarRoomService.listMentionableUsers(projectKey));
    }
}
