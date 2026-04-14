package com.aipal.controller;

import cn.hutool.core.util.IdUtil;
import com.aipal.common.Result;
import com.aipal.dto.A2AMessage;
import com.aipal.service.A2AMessageService;
import com.aipal.service.AgentRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/a2a")
@RequiredArgsConstructor
public class A2AController {

    private final A2AMessageService a2aMessageService;
    private final AgentRegistry agentRegistry;

    @PostMapping("/send")
    public Result<String> sendMessage(@Valid @RequestBody A2AMessage message) {
        if (message.getSessionId() == null) {
            message.setSessionId(IdUtil.fastSimpleUUID());
        }
        String messageId = a2aMessageService.sendMessage(message);
        return Result.success(messageId);
    }

    @GetMapping("/session/{sessionId}/messages")
    public Result<List<A2AMessage>> getSessionMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "100") long limit) {
        return Result.success(a2aMessageService.getSessionMessages(sessionId, limit));
    }

    @GetMapping("/response/{correlationId}")
    public Result<A2AMessage> getResponse(
            @PathVariable String correlationId,
            @RequestParam(defaultValue = "30000") long timeoutMs) {
        return Result.success(a2aMessageService.getResponse(correlationId, timeoutMs));
    }

    @GetMapping("/agents")
    public Result<List<AgentRegistry.AgentContext>> getRegisteredAgents() {
        return Result.success(agentRegistry.getAllAgents());
    }

    @GetMapping("/agent/{agentCode}")
    public Result<AgentRegistry.AgentContext> getAgent(@PathVariable String agentCode) {
        AgentRegistry.AgentContext ctx = agentRegistry.getAgent(agentCode);
        if (ctx == null) {
            return Result.error("Agent not found");
        }
        return Result.success(ctx);
    }

    @PostMapping("/agent/{agentCode}/refresh")
    public Result<Void> refreshAgent(@PathVariable String agentCode) {
        agentRegistry.refreshAgents();
        return Result.success(null);
    }
}
