package com.aipal.service;

import com.aipal.entity.AiAgent;
import com.aipal.entity.InvocationRecord;
import com.aipal.mapper.InvocationRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvocationService {

    private final InvocationRecordMapper recordMapper;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;

    public InvocationRecord invoke(Map<String, Object> request) {
        Long agentId = Long.valueOf(String.valueOf(request.get("agentId")));
        Object params = request.get("params");
        long start = System.currentTimeMillis();

        InvocationRecord record = new InvocationRecord();
        record.setAgentId(agentId);
        AiAgent agent = agentService.getAgentById(agentId);
        if (agent != null) record.setAgentCode(agent.getAgentCode());
        record.setTemplateCode((String) request.get("templateCode"));
        record.setInputParams(toJson(params));
        record.setStatus("RUNNING");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.insert(record);

        try {
            Object result = agentService.callAgent(agentId, params);
            record.setOutputResult(toJson(result));
            record.setStatus("SUCCESS");
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorMessage(e.getMessage());
        }
        record.setDurationMs((int) (System.currentTimeMillis() - start));
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        return record;
    }

    public Page<InvocationRecord> list(int pageNum, int pageSize, Long agentId, String status) {
        Page<InvocationRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InvocationRecord> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null) wrapper.eq(InvocationRecord::getAgentId, agentId);
        if (status != null && !status.isBlank()) wrapper.eq(InvocationRecord::getStatus, status);
        wrapper.orderByDesc(InvocationRecord::getCreateTime);
        return recordMapper.selectPage(page, wrapper);
    }

    public InvocationRecord retry(Long id) {
        InvocationRecord source = recordMapper.selectById(id);
        if (source == null) return null;
        return invoke(Map.of(
                "agentId", source.getAgentId(),
                "templateCode", source.getTemplateCode() == null ? "" : source.getTemplateCode(),
                "params", source.getInputParams() == null ? Map.of() : source.getInputParams()
        ));
    }

    public InvocationRecord get(Long id) {
        return recordMapper.selectById(id);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
