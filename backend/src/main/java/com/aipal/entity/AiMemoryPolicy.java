package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_memory_policy")
public class AiMemoryPolicy {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String scopeType;
    private String scopeKey;
    private Integer policyVersion;
    private Integer enabled;
    private String recallMode;
    private Integer retentionDays;
    private String allowedSources;
    private String maxSensitivity;
    private Integer vectorEnabled;
    private Integer sessionTokenBudget;
    private Integer workingTokenBudget;
    private Integer longTermTokenBudget;
    private Integer projectTokenBudget;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer isDeleted;
}
