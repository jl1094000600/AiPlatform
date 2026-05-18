package com.aipal.service;

import com.aipal.dto.AgentGraphEdge;
import com.aipal.dto.AgentGraphNode;
import com.aipal.dto.AgentGraphResponse;
import com.aipal.dto.ExecutionChainNode;
import com.aipal.dto.ExecutionChainResponse;
import com.aipal.entity.A2ATask;
import com.aipal.entity.AgentGraphEdgeConfig;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.A2ATaskMapper;
import com.aipal.mapper.AgentGraphEdgeConfigMapper;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AiAgentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MonitorService extends ServiceImpl<A2ATaskMapper, A2ATask> {
    private final AiAgentMapper agentMapper;
    private final AgentHeartbeatMapper heartbeatMapper;
    private final A2ATaskMapper a2aTaskMapper;
    private final AgentGraphEdgeConfigMapper edgeConfigMapper;

    public AgentGraphResponse getAgentGraph() {
        // Get all agents
        List<AiAgent> agents = agentMapper.selectList(null);

        // Get heartbeat info grouped by agentId
        List<AgentHeartbeat> heartbeats = heartbeatMapper.selectList(null);
        Map<String, List<AgentHeartbeat>> heartbeatByAgentCode = heartbeats.stream()
                .filter(h -> h.getAgentCode() != null && !h.getAgentCode().isBlank())
                .collect(Collectors.groupingBy(AgentHeartbeat::getAgentCode));
        Map<Long, List<AgentHeartbeat>> heartbeatByAgentId = heartbeats.stream()
                .filter(h -> h.getAgentId() != null)
                .collect(Collectors.groupingBy(AgentHeartbeat::getAgentId));

        // Build nodes
        List<AgentGraphNode> nodes = new ArrayList<>();
        for (AiAgent agent : agents) {
            AgentGraphNode node = new AgentGraphNode();
            node.setId(agent.getId());
            node.setName(agent.getAgentName());
            node.setType(agent.getCategory());

            List<AgentHeartbeat> agentHeartbeats = heartbeatByAgentCode.get(agent.getAgentCode());
            if (agentHeartbeats == null || agentHeartbeats.isEmpty()) {
                agentHeartbeats = heartbeatByAgentId.get(agent.getId());
            }
            if (agentHeartbeats != null && !agentHeartbeats.isEmpty()) {
                AgentHeartbeat latestHeartbeat = AgentRuntimeStatusSupport.latestHeartbeat(agentHeartbeats);
                node.setStatus(AgentRuntimeStatusSupport.runtimeStatus(latestHeartbeat));
                node.setLastHeartbeat(latestHeartbeat.getLastHeartbeat());
                node.setInstanceCount(AgentRuntimeStatusSupport.onlineInstanceCount(agentHeartbeats));
            } else {
                node.setStatus("offline");
                node.setInstanceCount(0);
            }
            nodes.add(node);
        }

        Map<String, AgentGraphEdge> edgeMap = new LinkedHashMap<>();

        List<AgentGraphEdgeConfig> configuredEdges = edgeConfigMapper.selectList(
                new LambdaQueryWrapper<AgentGraphEdgeConfig>()
                        .eq(AgentGraphEdgeConfig::getEnabled, 1)
        );
        for (AgentGraphEdgeConfig config : configuredEdges) {
            AgentGraphEdge edge = new AgentGraphEdge();
            edge.setEdgeId(config.getId());
            edge.setSource(config.getSourceAgentId());
            edge.setTarget(config.getTargetAgentId());
            edge.setSourceAgentCode(config.getSourceAgentCode());
            edge.setTargetAgentCode(config.getTargetAgentCode());
            edge.setEdgeSource("CONFIGURED");
            edge.setEdgeType(config.getEdgeType());
            edge.setTriggerIntent(config.getTriggerIntent());
            edge.setEnabled(config.getEnabled());
            edge.setSuitabilityLevel(config.getSuitabilityLevel());
            edge.setSuitabilityScore(config.getSuitabilityScore());
            edge.setSuitabilityMessage(config.getSuitabilityMessage());
            edge.setCallCount(0L);
            edge.setAvgResponseTime(0.0);
            edgeMap.put(edgeKey(config.getSourceAgentId(), config.getTargetAgentId()), edge);
        }

        // Build runtime edges from A2A tasks - use source_agent_id -> target_agent_id
        List<A2ATask> tasks = a2aTaskMapper.selectList(
                new LambdaQueryWrapper<A2ATask>()
                        .isNotNull(A2ATask::getSourceAgentId)
                        .isNotNull(A2ATask::getTargetAgentId)
                        .orderByDesc(A2ATask::getCreateTime)
                        .last("LIMIT 5000")
        );

        // Group by source -> target pair
        Map<String, List<A2ATask>> tasksByPair = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getSourceAgentId() + "_" + t.getTargetAgentId()));

        for (Map.Entry<String, List<A2ATask>> entry : tasksByPair.entrySet()) {
            List<A2ATask> pairTasks = entry.getValue();
            if (pairTasks.isEmpty()) continue;

            A2ATask firstTask = pairTasks.get(0);
            String key = edgeKey(firstTask.getSourceAgentId(), firstTask.getTargetAgentId());
            AgentGraphEdge edge = edgeMap.get(key);
            if (edge == null) {
                edge = new AgentGraphEdge();
                edge.setSource(firstTask.getSourceAgentId());
                edge.setTarget(firstTask.getTargetAgentId());
                edge.setEdgeSource("RUNTIME");
                edge.setEdgeType("OBSERVED");
                edge.setEnabled(1);
                edgeMap.put(key, edge);
            } else {
                edge.setEdgeSource("CONFIGURED_RUNTIME");
            }
            edge.setCallCount((long) pairTasks.size());

            // Calculate avg response time from duration (endTime - startTime)
            double avgResponse = pairTasks.stream()
                    .filter(t -> t.getStartTime() != null && t.getEndTime() != null)
                    .mapToDouble(t -> java.time.Duration.between(t.getStartTime(), t.getEndTime()).toMillis())
                    .average()
                    .orElse(0.0);
            edge.setAvgResponseTime(avgResponse);

            LocalDateTime lastCall = pairTasks.stream()
                    .filter(t -> t.getCreateTime() != null)
                    .map(A2ATask::getCreateTime)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            edge.setLastCallTime(lastCall);
        }

        AgentGraphResponse response = new AgentGraphResponse();
        response.setNodes(nodes);
        response.setEdges(new ArrayList<>(edgeMap.values()));
        return response;
    }

    private String edgeKey(Long sourceAgentId, Long targetAgentId) {
        return sourceAgentId + "_" + targetAgentId;
    }

    public ExecutionChainResponse getExecutionChain(String taskId, String sessionId) {
        LambdaQueryWrapper<A2ATask> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null && !taskId.isEmpty()) {
            wrapper.eq(A2ATask::getTaskId, taskId);
        } else if (sessionId != null && !sessionId.isEmpty()) {
            wrapper.eq(A2ATask::getSessionId, sessionId);
        } else {
            throw new RuntimeException("taskId or sessionId is required");
        }
        wrapper.orderByAsc(A2ATask::getCreateTime);

        List<A2ATask> tasks = a2aTaskMapper.selectList(wrapper);

        // Build agent id to name map
        List<AiAgent> agents = agentMapper.selectList(null);
        Map<Long, String> agentNameMap = agents.stream()
                .collect(Collectors.toMap(AiAgent::getId, AiAgent::getAgentName));

        List<ExecutionChainNode> chain = new ArrayList<>();
        for (A2ATask task : tasks) {
            ExecutionChainNode node = new ExecutionChainNode();
            node.setTaskId(task.getTaskId());
            node.setSourceAgentId(task.getSourceAgentId());
            node.setSourceAgentName(agentNameMap.getOrDefault(task.getSourceAgentId(), "Unknown"));
            node.setTargetAgentId(task.getTargetAgentId());
            node.setTargetAgentName(agentNameMap.getOrDefault(task.getTargetAgentId(), "Unknown"));
            node.setStatus(task.getStatus());
            node.setTaskType(task.getTaskType());
            node.setStartTime(task.getStartTime());
            node.setEndTime(task.getEndTime());
            if (task.getStartTime() != null && task.getEndTime() != null) {
                node.setDurationMs(java.time.Duration.between(task.getStartTime(), task.getEndTime()).toMillis());
            }
            chain.add(node);
        }

        ExecutionChainResponse response = new ExecutionChainResponse();
        if (!tasks.isEmpty()) {
            response.setSessionId(tasks.get(0).getSessionId());
            response.setWorkflowId(tasks.get(0).getWorkflowId() != null ? tasks.get(0).getWorkflowId().toString() : null);
        }
        response.setChain(chain);
        return response;
    }
}
