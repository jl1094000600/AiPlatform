package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_tts_task")
public class AiTtsTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;
    private String agentCode;
    private String textContent;
    private String voiceId;
    private Float speed;
    private Integer volume;
    private String outputFormat;
    private String status;
    private String audioUrl;
    private Float duration;
    private String errorMessage;
    private String sessionId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
