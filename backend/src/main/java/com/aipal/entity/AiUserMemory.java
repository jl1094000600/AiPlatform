package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_user_memory")
public class AiUserMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String memoryCode;
    private String userKey;
    private Long userId;
    private String username;
    private String sourceType;
    private Long sourceId;
    private String summaryContent;
    private Integer rawCount;
    private String compressionModel;
    private LocalDateTime memoryStartTime;
    private LocalDateTime memoryEndTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
