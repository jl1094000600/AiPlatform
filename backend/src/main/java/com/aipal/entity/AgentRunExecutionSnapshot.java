package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_run_execution_snapshot")
public class AgentRunExecutionSnapshot {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId; private Long runId; private String snapshotFormat; private String keyId;
    private String ivB64; private String ciphertextB64; private String plaintextHash;
    private LocalDateTime createTime; @TableLogic private Integer isDeleted;
}
