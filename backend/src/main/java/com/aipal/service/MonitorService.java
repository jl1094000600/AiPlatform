package com.aipal.service;

import com.aipal.dto.AgentGraphEdge;
import com.aipal.dto.AgentGraphNode;
import com.aipal.dto.AgentGraphResponse;
import com.aipal.dto.ExecutionChainNode;
import com.aipal.dto.ExecutionChainResponse;
import com.aipal.entity.A2ATask;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.A2ATaskMapper;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AiAgentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MonitorService extends ServiceImpl<A2ATaskMapper, A2ATask> {
    private final AiAgentMapper agentMapper;
    private final AgentHeartbeatMapper heartbeatMapper;
    private final A2ATaskMapper a2aTaskMapper;

    public AgentGraphResponse getAgentGraph() {
        // Get all agents
        List<AiAgent> agents = agentMapper.selectList(null);

        // Get heartbeat info grouped by agentId
        List<AgentHeartbeat> heartbeats = heartbeatMapper.selectList(null);
        Map<Long, List<AgentHeartbeat>> heartbeatByAgent = heartbeats.stream()
                .collect(Collectors.groupingBy(AgentHeartbeat::getAgentId));

        // Build nodes
        List<AgentGraphNode> nodes = new ArrayList<>();
        for (AiAgent agent : agents) {
            AgentGraphNode node = new AgentGraphNode();
            node.setId(agent.getId());
            node.setName(agent.getAgentName());
            node.setType(agent.getCategory());

            List<AgentHeartbeat> agentHeartbeats = heartbeatByAgent.get(agent.getId());
            if (agentHeartbeats != null && !agentHeartbeats.isEmpty()) {
                AgentHeartbeat latestHeartbeat = agentHeartbeats.stream()
                        .filter(h -> h.getLastHeartbeat() != null)
                        .max((h1, h2) -> h1.getLastHeartbeat().compareTo(h2.getLastHeartbeat()))
                        .orElse(agentHeartbeats.get(0));
                node.setStatus(latestHeartbeat.getStatus());
                node.setLastHeartbeat(latestHeartbeat.getLastHeartbeat());
                node.setInstanceCount(agentHeartbeats.size());
            } else {
                node.setStatus(0);
                node.setInstanceCount(0);
            }
            nodes.add(node);
        }

        // Build edges from A2A tasks - use source_agent_id -> target_agent_id
        List<A2ATask> tasks = a2aTaskMapper.selectList(
                new LambdaQueryWrapper<A2ATask>()
                        .orderByDesc(A2ATask::getCreateTime)
                        .last("LIMIT 5000")
        );

        // Group by source -> target pair
        Map<String, List<A2ATask>> tasksByPair = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getSourceAgentId() + "_" + t.getTargetAgentId()));

        List<AgentGraphEdge> edges = new ArrayList<>();
        for (Map.Entry<String, List<A2ATask>> entry : tasksByPair.entrySet()) {
            List<A2ATask> pairTasks = entry.getValue();
            if (pairTasks.isEmpty()) continue;

            A2ATask firstTask = pairTasks.get(0);
            AgentGraphEdge edge = new AgentGraphEdge();
            edge.setSource(firstTask.getSourceAgentId());
            edge.setTarget(firstTask.getTargetAgentId());
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

            edges.add(edge);
        }

        AgentGraphResponse response = new AgentGraphResponse();
        response.setNodes(nodes);
        response.setEdges(edges);
        return response;
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
