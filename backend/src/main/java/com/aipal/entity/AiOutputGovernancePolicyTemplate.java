package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_output_governance_policy_template")
public class AiOutputGovernancePolicyTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String policyCode;
    private String policyName;
    private String description;
    private String category;
    private String severity;
    private String targetArtifactType;
    private String detectorType;
    private String configJson;
    private Integer blockOnMatch;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
