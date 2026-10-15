package com.aipal.controller;

import com.aipal.agent.runtime.trace.RunTracer;
import com.aipal.common.Result;
import com.aipal.entity.AgentRun;
import com.aipal.security.RequirePermission;
import com.aipal.security.TenantContext;
import com.aipal.service.AgentRunStreamEventService;
import com.aipal.service.AgentRunService;
import com.aipal.service.ToolCallContractView;
import com.aipal.service.AgentToolResponseService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/agent-runs")
@RequiredArgsConstructor
public class AgentRunController {
    private final AgentRunService agentRunService;
    private final AgentRunStreamEventService streamEventService;
    private final AgentToolResponseService toolResponseService;
    private final RunTracer runTracer;

    @PostMapping
    @RequirePermission("agent:invoke")
    public Result<AgentRunView> create(@Valid @RequestBody CreateAgentRunRequest request) {
        return Result.success(toView(agentRunService.createOrReuse(new AgentRunService.CreateRunCommand(
                request.agentId(), request.projectKey(), request.businessType(), request.businessId(), request.input()))));
    }

    @GetMapping
    @RequirePermission("agent:invoke")
    public Result<Page<AgentRunView>> list(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize,
                                       @RequestParam(required = false) String status) {
        Page<AgentRun> page = agentRunService.list(pageNum, pageSize, status);
        Page<AgentRunView> view = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        view.setRecords(page.getRecords().stream().map(this::toView).toList());
        return Result.success(view);
    }

    @GetMapping("/{id}")
    @RequirePermission("agent:invoke")
    public Result<AgentRunView> get(@PathVariable Long id) { return Result.success(toView(agentRunService.get(id))); }

    @GetMapping("/{id}/detail")
    @RequirePermission("agent:invoke")
    public Result<AgentRunService.RunDetail> detail(@PathVariable Long id) {
        return Result.success(agentRunService.getDetail(id));
    }

    /** Tool-call list. {@code /tools} is the frontend contract path; {@code /tool-calls} kept as alias. */
    @GetMapping({"/{id}/tool-calls", "/{id}/tools"})
    @RequirePermission("agent:invoke")
    public Result<List<ToolCallContractView>> toolCalls(@PathVariable Long id) {
        return Result.success(agentRunService.listToolCalls(id).stream()
                .map(ToolCallContractView::from)
                .toList());
    }

    /** Span timeline. Contains no prompt bodies and no memory bodies. */
    @GetMapping("/{id}/trace")
    @RequirePermission("agent:invoke")
    public Result<RunTracer.TraceTimeline> trace(@PathVariable Long id) {
        AgentRun run = agentRunService.get(id);
        return Result.success(runTracer.timeline(run.getTenantId(), run.getId(), run.getTraceId(), run.getStatus()));
    }

    /** Answers an in-flight ask_user tool call. */
    @PostMapping("/{id}/tools/{callId}/respond")
    @RequirePermission("agent:invoke")
    public Result<AgentToolResponseService.RespondResult> respondToTool(
            @PathVariable Long id, @PathVariable String callId,
            @Valid @RequestBody RespondToolRequest request) {
        agentRunService.assertCanRespondToUserInput(id);
        return Result.success(toolResponseService.respondToToolCall(id, callId, request.answer()));
    }

    @GetMapping("/{id}/stream")
    @RequirePermission("agent:invoke")
    public Result<java.util.List<AgentRunStreamEventService.RunStreamEventView>> stream(
            @PathVariable Long id,
            @RequestParam(required = false) Long afterSequenceNo,
            @RequestParam(defaultValue = "200") int limit) {
        AgentRun run = agentRunService.get(id);
        boolean includeRestricted = TenantContext.hasPermission("agent:manage")
                || TenantContext.hasPermission("memory:policy");
        return Result.success(streamEventService.listForRun(run.getTenantId(), run.getId(), afterSequenceNo, limit,
                includeRestricted));
    }

    @PostMapping("/{id}/cancel")
    @RequirePermission("agent:invoke")
    public Result<AgentRunView> cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        return Result.success(toView(agentRunService.cancel(id, request == null ? null : request.reason())));
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("agent:invoke")
    public Result<AgentRunView> approve(@PathVariable Long id, @RequestBody(required = false) ApprovalRequest request) {
        return Result.success(toView(agentRunService.approve(id, request == null ? null : request.reason())));
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("agent:invoke")
    public Result<AgentRunView> reject(@PathVariable Long id, @RequestBody(required = false) ApprovalRequest request) {
        return Result.success(toView(agentRunService.reject(id, request == null ? null : request.reason())));
    }

    public record CreateAgentRunRequest(@NotNull Long agentId, @NotBlank String projectKey,
                                        @NotBlank String businessType, @NotBlank String businessId, Object input) {}
    public record CancelRequest(String reason) {}
    public record ApprovalRequest(String reason) {}
    public record RespondToolRequest(@NotBlank String answer) {}
    public record AgentRunView(Long id, String projectKey, String businessType, String businessId,
                               Long agentId, Long agentVersionId, String status, String traceId, String memoryTraceId,
                               Integer totalTokens, String errorMessage, boolean canCancel, boolean canApprove, LocalDateTime createTime,
                               LocalDateTime startTime, LocalDateTime endTime) {}

    private AgentRunView toView(AgentRun run) {
        AgentRunService.RunView view = agentRunService.businessView(run);
        return new AgentRunView(view.id(), view.projectKey(), view.businessType(), view.businessId(),
                view.agentId(), view.agentVersionId(), view.status(), view.traceId(), view.memoryTraceId(),
                view.totalTokens(), view.errorMessage(), view.canCancel(), view.canApprove(), view.createTime(), view.startTime(), view.endTime());
    }
}

