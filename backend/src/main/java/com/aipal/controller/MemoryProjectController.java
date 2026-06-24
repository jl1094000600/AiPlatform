package com.aipal.controller;

import com.aipal.common.Result;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.AiMemoryProjectMember;
import com.aipal.security.RequirePermission;
import com.aipal.service.memory.MemoryProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/memory-projects")
@RequiredArgsConstructor
public class MemoryProjectController {

    private final MemoryProjectService projectService;

    @GetMapping
    @RequirePermission("memory:list")
    public Result<List<AiMemoryProject>> listAccessible() {
        return Result.success(projectService.listAccessible());
    }

    @PostMapping
    @RequirePermission("memory:write")
    public Result<AiMemoryProject> create(@Valid @RequestBody CreateProjectRequest request) {
        return Result.success(projectService.create(request.projectKey(), request.projectName(), request.projectType()));
    }

    @PostMapping("/{projectKey}/members")
    @RequirePermission("memory:write")
    public Result<AiMemoryProjectMember> addMember(@PathVariable String projectKey,
                                                     @Valid @RequestBody AddMemberRequest request) {
        return Result.success(projectService.addMember(projectKey, request.userId(), request.memberRole()));
    }

    public record CreateProjectRequest(@NotBlank String projectKey, @NotBlank String projectName, String projectType) {
    }

    public record AddMemberRequest(@NotNull Long userId, String memberRole) {
    }
}
