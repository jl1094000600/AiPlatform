package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_evaluation")
public class AiEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String evaluationCode;
    private String evaluationName;
    private String description;
    private Long datasetId;
    private Long agentId;
    private String criteriaConfig;
    private String resultData;
    private Double totalScore;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long executorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}