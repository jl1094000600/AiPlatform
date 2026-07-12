package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_context_snapshot")
public class AgentContextSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long runId;
    private Integer snapshotVersion;
    private String snapshotType;
    private String contextSummary;
    private String rawContextHash;
    private Integer inputTokens;
    private Integer outputTokens;
    private String compactReason;
    private LocalDateTime createTime;
    @TableLogic
    private Integer isDeleted;
}
