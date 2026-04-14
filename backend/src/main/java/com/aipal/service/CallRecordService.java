package com.aipal.service;

import com.aipal.entity.MonCallRecord;
import com.aipal.entity.AiAgent;
import com.aipal.mapper.MonCallRecordMapper;
import com.aipal.mapper.AiAgentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CallRecordService {
    private final MonCallRecordMapper callRecordMapper;
    private final AiAgentMapper agentMapper;

    public Page<MonCallRecord> listCallRecords(int pageNum, int pageSize, Long agentId, LocalDateTime startTime, LocalDateTime endTime) {
        Page<MonCallRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null) {
            wrapper.eq(MonCallRecord::getAgentId, agentId);
        }
        if (startTime != null) {
            wrapper.ge(MonCallRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(MonCallRecord::getCreateTime, endTime);
        }
        wrapper.orderByDesc(MonCallRecord::getCreateTime);
        return callRecordMapper.selectPage(page, wrapper);
    }

    public boolean saveCallRecord(MonCallRecord record) {
        return callRecordMapper.insert(record) > 0;
    }

    public MonCallRecord getByTraceId(String traceId) {
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonCallRecord::getTraceId, traceId);
        return callRecordMapper.selectOne(wrapper);
    }

    public Long getTotalCallsByAgentId(Long agentId) {
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MonCallRecord::getAgentId, agentId);
        return callRecordMapper.selectCount(wrapper);
    }

    public List<MonCallRecord> getRecentRecords(int limit) {
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MonCallRecord::getCreateTime);
        wrapper.last("LIMIT ?", limit);
        return callRecordMapper.selectList(wrapper);
    }

    public Long countByAgentAndTimeRange(Long agentId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null) {
            wrapper.eq(MonCallRecord::getAgentId, agentId);
        }
        if (startTime != null) {
            wrapper.ge(MonCallRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(MonCallRecord::getCreateTime, endTime);
        }
        return callRecordMapper.selectCount(wrapper);
    }

    public Double getSuccessRateByAgentAndTimeRange(Long agentId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null) {
            wrapper.eq(MonCallRecord::getAgentId, agentId);
        }
        if (startTime != null) {
            wrapper.ge(MonCallRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(MonCallRecord::getCreateTime, endTime);
        }
        long total = callRecordMapper.selectCount(wrapper);
        if (total == 0) {
            return 0.0;
        }
        wrapper.eq(MonCallRecord::getSuccess, 1);
        long success = callRecordMapper.selectCount(wrapper);
        return (double) success / total * 100;
    }

    public Double getAvgDurationByAgentAndTimeRange(Long agentId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null) {
            wrapper.eq(MonCallRecord::getAgentId, agentId);
        }
        if (startTime != null) {
            wrapper.ge(MonCallRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(MonCallRecord::getCreateTime, endTime);
        }
        List<MonCallRecord> records = callRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return 0.0;
        }
        double avg = records.stream()
                .filter(r -> r.getDurationMs() != null)
                .mapToInt(MonCallRecord::getDurationMs)
                .average()
                .orElse(0.0);
        return avg;
    }

    public Long countOnlineAgents() {
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAgent::getStatus, 1);
        return agentMapper.selectCount(wrapper);
    }

    public Double getCurrentQps() {
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(MonCallRecord::getCreateTime, oneMinuteAgo);
        long count = callRecordMapper.selectCount(wrapper);
        return (double) count / 60;
    }

    public Double getAvgResponseTime() {
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        LambdaQueryWrapper<MonCallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(MonCallRecord::getCreateTime, oneMinuteAgo);
        List<MonCallRecord> records = callRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return 0.0;
        }
        return records.stream()
                .filter(r -> r.getDurationMs() != null)
                .mapToInt(MonCallRecord::getDurationMs)
                .average()
                .orElse(0.0);
    }
}
