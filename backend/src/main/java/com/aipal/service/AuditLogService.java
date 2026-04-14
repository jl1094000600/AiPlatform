package com.aipal.service;

import com.aipal.entity.SysAuditLog;
import com.aipal.mapper.SysAuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
}
