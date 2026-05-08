package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_quality_result")
public class AiAgentQualityResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Integer sampleIndex;
    private String inputText;
    private String expectedOutput;
    private String predictedOutput;
    private Integer matched;
    private Integer durationMs;
    private String errorMessage;
    private LocalDateTime createTime;
}
