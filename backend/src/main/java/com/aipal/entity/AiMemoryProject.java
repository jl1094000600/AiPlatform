package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_memory_project")
public class AiMemoryProject {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String projectKey;
    private String projectName;
    private String projectType;
    private Long ownerUserId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer isDeleted;
}
