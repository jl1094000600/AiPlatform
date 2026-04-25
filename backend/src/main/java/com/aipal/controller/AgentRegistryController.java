package com.aipal.controller;

import com.aipal.dto.AgentRegisterRequest;
import com.aipal.dto.AgentRegisterResponse;
import com.aipal.dto.HeartbeatRequest;
import com.aipal.entity.AgentRegistration;
import com.aipal.service.AgentRegistryService;
import com.aipal.service.HeartbeatManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent注册Controller
 * 提供Agent的注册、注销、查询等REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentRegistryController {

    private final AgentRegistryService agentRegistryService;
    private final HeartbeatManagementService heartbeatManagementService;

    /**
     * Push模式注册Agent
     * @param request 注册请求
     * @return 注册响应
     */
    @PostMapping
    public ResponseEntity<AgentRegisterResponse> register(
            @Valid @RequestBody AgentRegisterRequest request) {
        log.info("Received agent registration request: {}", request.getAgentCode());
        AgentRegisterResponse response = agentRegistryService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 注销Agent
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @return 无内容
     */
    @DeleteMapping("/{agentCode}")
    public ResponseEntity<Void> unregister(
            @PathVariable String agentCode,
            @RequestParam(defaultValue = "default") String instanceId) {
        log.info("Unregistering agent: {} [{}]", agentCode, instanceId);
        agentRegistryService.unregister(agentCode, instanceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取Agent注册信息
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @return 注册信息
     */
    @GetMapping("/{agentCode}")
    public ResponseEntity<AgentRegistration> getRegistration(
            @PathVariable String agentCode,
            @RequestParam(defaultValue = "default") String instanceId) {
        AgentRegistration registration = agentRegistryService.getRegistration(agentCode, instanceId);
        if (registration == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(registration);
    }

    /**
     * 获取所有已注册的Agent
     * @return 注册列表
     */
    @GetMapping
    public ResponseEntity<List<AgentRegistration>> getAllRegistrations() {
        List<AgentRegistration> registrations = agentRegistryService.getAllRegistrations();
        return ResponseEntity.ok(registrations);
    }

    /**
     * 手动刷新注册列表
     * @return 无内容
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshRegistrations() {
        agentRegistryService.refreshRegistrations();
        return ResponseEntity.noContent().build();
    }

    /**
     * 心跳上报
     * @param request 心跳请求
     * @return 无内容
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        heartbeatManagementService.recordHeartbeat(request);
        return ResponseEntity.ok().build();
    }

    /**
     * 探测Agent健康状态（Pull模式）
     * @param agentCode Agent编码
     * @param instanceId 实例ID
     * @return 是否在线
     */
    @GetMapping("/{agentCode}/probe")
    public ResponseEntity<Boolean> probeAgent(
            @PathVariable String agentCode,
            @RequestParam(defaultValue = "default") String instanceId) {
        boolean online = agentRegistryService.probeAgent(agentCode, instanceId);
        return ResponseEntity.ok(online);
    }
}
