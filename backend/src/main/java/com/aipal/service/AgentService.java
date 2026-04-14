package com.aipal.service;

import com.aipal.common.TraceContext;
import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentVersion;
import com.aipal.entity.MonCallRecord;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.MonCallRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AiAgentMapper agentMapper;
    private final AgentVersionService agentVersionService;
    private final MonCallRecordMapper callRecordMapper;

    public Page<AiAgent> listAgents(int pageNum, int pageSize, String name, Integer status, String category) {
        Page<AiAgent> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.like(AiAgent::getAgentName, name);
        }
        if (status != null) {
            wrapper.eq(AiAgent::getStatus, status);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(AiAgent::getCategory, category);
        }
        wrapper.orderByDesc(AiAgent::getCreateTime);
        return agentMapper.selectPage(page, wrapper);
    }

    public AiAgent getAgentById(Long id) {
        return agentMapper.selectById(id);
    }

    public boolean saveAgent(AiAgent agent) {
        if (agent.getStatus() == null) {
            agent.setStatus(2);
        }
        return agentMapper.insert(agent) > 0;
    }

    public boolean updateAgent(AiAgent agent) {
        return agentMapper.updateById(agent) > 0;
    }

    public boolean deleteAgent(Long id) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent != null && agent.getStatus() == 1) {
            throw new RuntimeException("只能删除已下线的Agent");
        }
        return agentMapper.deleteById(id) > 0;
    }

    public boolean publish(Long id) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new RuntimeException("Agent不存在");
        }
        if (agent.getStatus() == 1) {
            throw new RuntimeException("Agent已是上线状态");
        }

        AiAgentVersion latestVersion = agentVersionService.getLatestVersion(id);
        if (latestVersion == null) {
            AiAgentVersion version = new AiAgentVersion();
            version.setAgentId(id);
            version.setVersion("1.0.0");
            version.setStatus(1);
            agentVersionService.saveVersion(version);
        }

        agent.setStatus(1);
        return agentMapper.updateById(agent) > 0;
    }

    public boolean offline(Long id) {
        AiAgent agent = new AiAgent();
        agent.setId(id);
        agent.setStatus(2);
        return agentMapper.updateById(agent) > 0;
    }

    public List<AiAgent> getOnlineAgents() {
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAgent::getStatus, 1);
        return agentMapper.selectList(wrapper);
    }

    public Map<String, Object> callAgent(Long agentId, Object params) {
        AiAgent agent = agentMapper.selectById(agentId);
        if (agent == null || agent.getStatus() != 1) {
            throw new RuntimeException("Agent不存在或未上线");
        }

        String traceId = TraceContext.generateTraceId();
        LocalDateTime requestTime = LocalDateTime.now();

        MonCallRecord record = new MonCallRecord();
        record.setTraceId(traceId);
        record.setAgentId(agentId);
        record.setBizModuleId(1L);
        record.setRequestTime(requestTime);
        record.setSuccess(1);

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("traceId", traceId);
            result.put("status", "success");
            result.put("message", "Agent调用成功");
            result.put("agentName", agent.getAgentName());

            record.setResponseTime(LocalDateTime.now());
            record.setDurationMs(100);
            record.setInputTokens(100);
            record.setOutputTokens(200);
            record.setTotalTokens(300);
            record.setStatusCode(200);

            return result;
        } catch (Exception e) {
            record.setSuccess(0);
            record.setErrorMessage(e.getMessage());
            record.setStatusCode(500);
            throw e;
        } finally {
            callRecordMapper.insert(record);
        }
    }
}
