package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_run_stream_event")
public class AgentRunStreamEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long runId;
    private String eventType;
    private Long sequenceNo;
    private String payloadJson;
    private Integer visibleToBusiness;
    private LocalDateTime createTime;
    @TableLogic
    private Integer isDeleted;
}
