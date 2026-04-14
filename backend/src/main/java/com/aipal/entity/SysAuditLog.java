package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String operation;
    private String resourceType;
    private Long resourceId;
    private String resourceCode;
    private String beforeValue;
    private String afterValue;
    private String ipAddress;
    private String userAgent;
    private Integer result;
    private String errorMessage;
    private LocalDateTime createTime;
}
