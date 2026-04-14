package com.aipal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CallRecordItem {
    private String taskId;
    private Long sourceAgentId;
    private String sourceAgentName;
    private Long targetAgentId;
    private String targetAgentName;
    private String callType;
    private String taskType;
    private String taskDescription;
    private String status;
    private Long duration;
    private String errorMessage;
    private LocalDateTime createTime;
}
