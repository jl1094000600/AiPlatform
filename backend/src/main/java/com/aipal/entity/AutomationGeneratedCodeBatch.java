package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_generated_code_batch")
public class AutomationGeneratedCodeBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pipelineId;
    private Long stageRunId;
    private Long generationJobId;
    private String artifactPath;
    private String manifestJson;
    private Integer fileCount;
    private Long totalBytes;
    private String modelCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
