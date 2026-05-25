package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_engineering_test_case")
public class PromptEngineeringTestCase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long promptId;
    private String caseName;
    private String inputJson;
    private String expectedOutput;
    private String scoringRule;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
