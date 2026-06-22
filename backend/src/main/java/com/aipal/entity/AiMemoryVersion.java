package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_memory_version")
public class AiMemoryVersion {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long memoryId;
    private Integer version;
    private String title;
    private String content;
    private String factJson;
    private String status;
    private String changeType;
    private String changeReason;
    private String changedBy;
    private LocalDateTime createTime;
}
