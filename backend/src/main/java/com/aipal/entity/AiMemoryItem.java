package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_memory_item")
public class AiMemoryItem {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String memoryCode;
    private String memoryType;
    private String scopeType;
    private String scopeKey;
    private String projectType;
    private String projectKey;
    private Long ownerUserId;
    private String ownerUsername;
    private String title;
    private String content;
    private String factJson;
    private String sourceType;
    private String sourceRef;
    private Long legacyMemoryId;
    private String sensitivity;
    private Integer importance;
    private BigDecimal confidence;
    private String status;
    private Integer version;
    private LocalDateTime validFrom;
    private LocalDateTime expiresAt;
    private LocalDateTime lastRecalledAt;
    private Long recallCount;
    private String createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer isDeleted;
}
