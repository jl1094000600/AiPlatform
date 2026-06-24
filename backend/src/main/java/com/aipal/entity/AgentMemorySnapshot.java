package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_memory_snapshot")
public class AgentMemorySnapshot {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId; private Long runId; private Integer snapshotVersion; private Long memoryId; private Integer memoryVersion;
    private String memoryCode; private String sourceType; private String scopeType; private Integer tokenCount; private Integer policyVersion;
    private String traceId; private String contentSummary; private LocalDateTime createTime; @TableLogic private Integer isDeleted;
}
