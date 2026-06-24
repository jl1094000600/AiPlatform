package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_artifact")
public class AgentArtifact {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId; private Long runId; private Long stepId; private String artifactType; private String title;
    private String storagePath; private String contentJson; private String status; private LocalDateTime createTime; private LocalDateTime updateTime;
    @TableLogic private Integer isDeleted;
}
