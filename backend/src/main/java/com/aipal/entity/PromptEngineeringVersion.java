package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_engineering_version")
public class PromptEngineeringVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long promptId;
    private Integer versionNo;
    private String versionName;
    private String systemPrompt;
    private String userPromptTemplate;
    private String variableDefinitions;
    private String changelog;
    private String status;
    private Integer latestScore;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
