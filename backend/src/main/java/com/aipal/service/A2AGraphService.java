package com.aipal.service;

import com.aipal.dto.*;
import com.aipal.entity.A2ATask;
import com.aipal.entity.AgentHeartbeat;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.A2ATaskMapper;
import com.aipal.mapper.AgentHeartbeatMapper;
import com.aipal.mapper.AiAgentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class A2AGraphService {
    private final AiAgentMapper agentMapper;
    private final AgentHeartbeatMapper heartbeatMapper;
    private final A2ATaskMapper taskMapper;

    public AgentDetailResponse getAgentDetail(Long agentId) {
        AiAgent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new RuntimeException("Agent不存在: " + agentId);
        }

        AgentDetailResponse response = new AgentDetailResponse();
        response.setAgentId(agent.getId());
        response.setAgentName(agent.getAgentName());
        response.setDescription(agent.getDescription());
        response.setAgentType(agent.getCategory());

        // Get heartbeat instances
        List<AgentHeartbeat> heartbeats = heartbeatMapper.selectList(
                new LambdaQueryWrapper<AgentHeartbeat>().eq(AgentHeartbeat::getAgentId, agentId)
        );
        List<AgentInstance> instances = new ArrayList<>();
        for (AgentHeartbeat hb : heartbeats) {
            AgentInstance inst = new AgentInstance();
            inst.setInstanceId(hb.getInstanceId());
            inst.setStatus(hb.getStatus() != null && hb.getStatus() == 1 ? "ONLINE" : "OFFLINE");
            inst.setLastHeartbeat(hb.getLastHeartbeat());
            instances.add(inst);
        }
        response.setInstances(instances);

        // Set overall status from latest heartbeat
        if (!heartbeats.isEmpty()) {
            AgentHeartbeat latest = heartbeats.stream()
                    .filter(h -> h.getLastHeartbeat() != null)
                    .max((h1, h2) -> h1.getLastHeartbeat().compareTo(h2.getLastHeartbeat()))
                    .orElse(heartbeats.get(0));
            response.setStatus(latest.getStatus() != null && latest.getStatus() == 1 ? "ONLINE" : "OFFLINE");
        } else {
            response.setStatus("OFFLINE");
        }

        // Get upstream agents (agents that call this agent)
        List<A2ATask> upstreamTasks = taskMapper.selectList(
                new LambdaQueryWrapper<A2ATask>()
                        .eq(A2ATask::getTargetAgentId, agentId)
                        .ge(A2ATask::getCreateTime, LocalDateTime.now().with(LocalTime.MIN))
        );
        Map<Long, Long> upstreamCountMap = upstreamTasks.stream()
                .collect(Collectors.groupingBy(A2ATask::getSourceAgentId, Collectors.counting()));
        List<AgentRef> upstreamAgents = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : upstreamCountMap.entrySet()) {
            AiAgent upstream = agentMapper.selectById(entry.getKey());
            if (upstream != null) {
                AgentRef ref = new AgentRef();
                ref.setAgentId(upstream.getId());
                ref.setAgentName(upstream.getAgentName());
                ref.setCallType("SYNC");
                ref.setCallCountPerHour(entry.getValue().intValue());
                upstreamAgents.add(ref);
            }
        }
        response.setUpstreamAgents(upstreamAgents);

        // Get downstream agents (agents that this agent calls)
        List<A2ATask> downstreamTasks = taskMapper.selectList(
                new LambdaQueryWrapper<A2ATask>()
                        .eq(A2ATask::getSourceAgentId, agentId)
                        .ge(A2ATask::getCreateTime, LocalDateTime.now().with(LocalTime.MIN))
        );
        Map<Long, Long> downstreamCountMap = downstreamTasks.stream()
                .collect(Collectors.groupingBy(A2ATask::getTargetAgentId, Collectors.counting()));
        List<AgentRef> downstreamAgents = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : downstreamCountMap.entrySet()) {
            AiAgent downstream = agentMapper.selectById(entry.getKey());
            if (downstream != null) {
                AgentRef ref = new AgentRef();
                ref.setAgentId(downstream.getId());
                ref.setAgentName(downstream.getAgentName());
                ref.setCallType("SYNC");
                ref.setCallCountPerHour(entry.getValue().intValue());
                downstreamAgents.add(ref);
            }
        }
        response.setDownstreamAgents(downstreamAgents);

        // Today stats
        AgentStats stats = new AgentStats();
        stats.setTotalCalls((long) upstreamTasks.size() + downstreamTasks.size());
        long successCount = upstreamTasks.stream().filter(t -> "success".equals(t.getStatus())).count()
                + downstreamTasks.stream().filter(t -> "success".equals(t.getStatus())).count();
        stats.setSuccessCalls(successCount);
        stats.setFailedCalls(stats.getTotalCalls() - successCount);
        response.setTodayStats(stats);

        return response;
    }

    public PageResult<CallRecordItem> getAgentCalls(Long agentId, int page, int pageSize,
                                                     String callType, String status,
                                                     LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<A2ATask> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(A2ATask::getSourceAgentId, agentId).or().eq(A2ATask::getTargetAgentId, agentId));

        if (callType != null && !callType.isEmpty()) {
            wrapper.eq(A2ATask::getTaskType, callType);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(A2ATask::getStatus, status);
        }
        if (startTime != null) {
            wrapper.ge(A2ATask::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(A2ATask::getCreateTime, endTime);
        }
        wrapper.orderByDesc(A2ATask::getCreateTime);

        // Get total count
        long total = taskMapper.selectCount(wrapper);

        // Paginate
        int offset = (page - 1) * pageSize;
        wrapper.last("LIMIT " + offset + ", " + pageSize);

        List<A2ATask> tasks = taskMapper.selectList(wrapper);

        // Build agent name map
        List<Long> agentIds = tasks.stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getSourceAgentId(), t.getTargetAgentId()))
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> agentNameMap = agentMapper.selectBatchIds(agentIds).stream()
                .collect(Collectors.toMap(AiAgent::getId, AiAgent::getAgentName));

        List<CallRecordItem> records = new ArrayList<>();
        for (A2ATask task : tasks) {
            CallRecordItem item = new CallRecordItem();
            item.setTaskId(task.getTaskId());
            item.setSourceAgentId(task.getSourceAgentId());
            item.setSourceAgentName(agentNameMap.getOrDefault(task.getSourceAgentId(), "Unknown"));
            item.setTargetAgentId(task.getTargetAgentId());
            item.setTargetAgentName(agentNameMap.getOrDefault(task.getTargetAgentId(), "Unknown"));
            item.setTaskType(task.getTaskType());
            item.setTaskDescription(task.getTaskDescription());
            item.setStatus(task.getStatus());
            item.setCreateTime(task.getCreateTime());
            if (task.getStartTime() != null && task.getEndTime() != null) {
                item.setDuration(java.time.Duration.between(task.getStartTime(), task.getEndTime()).toMillis());
            }
            records.add(item);
        }

        return PageResult.of(records, total, page, pageSize);
    }

    public Object exportGraph(String format) {
        // Return current graph data for export
        // In production, this would generate JSON or Excel file
        return java.util.Map.of(
                "exportTime", LocalDateTime.now(),
                "message", "Export functionality - format: " + (format != null ? format : "json")
        );
    }

    public ExecutionDetail getExecutionDetail(String executionId) {
        // Find the task by task_id (executionId)
        A2ATask task = taskMapper.selectOne(
                new LambdaQueryWrapper<A2ATask>().eq(A2ATask::getTaskId, executionId)
        );
        if (task == null) {
            throw new RuntimeException("执行记录不存在: " + executionId);
        }

        // Get all tasks in the same session to build the chain
        List<A2ATask> sessionTasks = taskMapper.selectList(
                new LambdaQueryWrapper<A2ATask>()
                        .eq(A2ATask::getSessionId, task.getSessionId())
                        .orderByAsc(A2ATask::getCreateTime)
        );

        // Build agent name map
        List<Long> agentIds = sessionTasks.stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getSourceAgentId(), t.getTargetAgentId()))
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> agentNameMap = agentMapper.selectBatchIds(agentIds).stream()
                .collect(Collectors.toMap(AiAgent::getId, AiAgent::getAgentName));

        ExecutionDetail detail = new ExecutionDetail();
        detail.setExecutionId(task.getTaskId());
        detail.setWorkflowId(task.getWorkflowId() != null ? task.getWorkflowId().toString() : null);
        detail.setStatus(task.getStatus());
        detail.setStartTime(task.getStartTime());
        detail.setEndTime(task.getEndTime());
        if (task.getStartTime() != null && task.getEndTime() != null) {
            detail.setTotalDuration(java.time.Duration.between(task.getStartTime(), task.getEndTime()).toMillis());
        }

        List<NodeExecution> nodes = new ArrayList<>();
        int index = 1;
        for (A2ATask t : sessionTasks) {
            NodeExecution node = new NodeExecution();
            node.setNodeId("node-" + index++);
            node.setNodeName(t.getTaskDescription() != null ? t.getTaskDescription() : t.getTaskType());
            node.setNodeType("AGENT");
            node.setAgentId(t.getTargetAgentId());
            node.setAgentName(agentNameMap.getOrDefault(t.getTargetAgentId(), "Unknown"));
            node.setStatus(t.getStatus());
            node.setStartTime(t.getStartTime());
            node.setEndTime(t.getEndTime());
            if (t.getStartTime() != null && t.getEndTime() != null) {
                node.setDuration(java.time.Duration.between(t.getStartTime(), t.getEndTime()).toMillis());
            }
            node.setParallelIndex(0);
            nodes.add(node);
        }
        detail.setNodes(nodes);

        return detail;
    }
}
