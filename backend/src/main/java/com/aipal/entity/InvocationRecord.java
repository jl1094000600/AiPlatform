package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lowcode_invocation_record")
public class InvocationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String agentCode;
    private String templateCode;
    private String inputParams;
    private String outputResult;
    private String status;
    private String errorMessage;
    private Integer durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
