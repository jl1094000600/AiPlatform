package com.aipal.dto;

import com.aipal.entity.AutomationApproval;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import lombok.Data;

import java.util.List;

@Data
public class AutomationPipelineDetail {
    private AutomationPipeline pipeline;
    private List<AutomationStageRun> stages;
    private List<AutomationApproval> approvals;
}
