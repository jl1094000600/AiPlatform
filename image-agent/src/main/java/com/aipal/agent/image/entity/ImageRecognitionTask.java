package com.aipal.agent.image.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("image_recognition_task")
public class ImageRecognitionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Long agentId;
    private String instanceId;
    private String inputType;
    private String inputData;
    private String fileType;
    private String result;
    private Integer status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
