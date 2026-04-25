package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_dataset")
public class AiDataset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String datasetCode;
    private String datasetName;
    private String description;
    private String category;
    private String format;
    private Long size;
    private String filePath;
    private Integer recordCount;
    private String fieldSchema;
    private Integer status;
    private Long ownerId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}