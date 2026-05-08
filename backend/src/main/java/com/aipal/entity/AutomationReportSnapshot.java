package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("automation_report_snapshot")
public class AutomationReportSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate snapshotDate;
    private String productLine;
    private Integer totalCount;
    private Integer runningCount;
    private Integer completedCount;
    private Integer blockedCount;
    private Double passRate;
    private LocalDateTime createTime;
}
