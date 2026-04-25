package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_tts_config")
public class AiTtsConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;
    private String provider;
    private String defaultVoice;
    private String apiKey;
    private String apiEndpoint;
    private Integer maxTextLength;
    private String outputFormat;
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
