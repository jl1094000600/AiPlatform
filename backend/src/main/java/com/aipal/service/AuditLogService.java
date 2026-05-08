package com.aipal.service;

import com.aipal.entity.SysAuditLog;
import com.aipal.mapper.SysAuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService extends ServiceImpl<SysAuditLogMapper, SysAuditLog> {

    public Page<SysAuditLog> listAuditLogs(int pageNum, int pageSize, Long userId, String operation,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        Page<SysAuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(SysAuditLog::getUserId, userId);
        }
        if (operation != null && !operation.isEmpty()) {
            wrapper.eq(SysAuditLog::getOperation, operation);
        }
        if (startTime != null) {
            wrapper.ge(SysAuditLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysAuditLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(SysAuditLog::getCreateTime);
        return this.page(page, wrapper);
    }

    public SysAuditLog getAuditLogById(Long id) {
        return this.getById(id);
    }

    public byte[] exportAuditLogs(Long userId, String operation, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) wrapper.eq(SysAuditLog::getUserId, userId);
        if (operation != null && !operation.isEmpty()) wrapper.eq(SysAuditLog::getOperation, operation);
        if (startTime != null) wrapper.ge(SysAuditLog::getCreateTime, startTime);
        if (endTime != null) wrapper.le(SysAuditLog::getCreateTime, endTime);
        wrapper.orderByDesc(SysAuditLog::getCreateTime);
        List<SysAuditLog> logs = this.list(wrapper);

        StringBuilder csv = new StringBuilder("id,userId,username,operation,resourceType,result,errorMessage,createTime\n");
        for (SysAuditLog log : logs) {
            csv.append(value(log.getId())).append(',')
                    .append(value(log.getUserId())).append(',')
                    .append(value(log.getUsername())).append(',')
                    .append(value(log.getOperation())).append(',')
                    .append(value(log.getResourceType())).append(',')
                    .append(value(log.getResult())).append(',')
                    .append(value(log.getErrorMessage())).append(',')
                    .append(value(log.getCreateTime())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Object value(Object value) {
        if (value == null) return "";
        return String.valueOf(value).replace(",", " ");
    }
}
