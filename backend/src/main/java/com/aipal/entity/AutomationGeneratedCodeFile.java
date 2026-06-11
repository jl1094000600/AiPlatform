package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("automation_generated_code_file")
public class AutomationGeneratedCodeFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long pipelineId;
    private Long stageRunId;
    private Long generationJobId;
    private Integer fileIndex;
    private String filePath;
    private String fileType;
    private Long sizeBytes;
    private String contentHash;
    private String content;
    private LocalDateTime createTime;
}
