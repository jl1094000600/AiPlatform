package com.aipal.service;

import com.aipal.dto.AutomationPipelineRequest;
import com.aipal.dto.RequirementDraftMergeRequest;
import com.aipal.dto.RequirementPrdRequest;
import com.aipal.entity.AiModel;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.entity.RequirementAttachment;
import com.aipal.entity.RequirementParseTask;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RequirementDraftService {
    private static final String REQUIREMENT_STAGE = "requirement_analysis";

    private final RequirementAttachmentService attachmentService;
    private final AutomationPipelineService automationPipelineService;
    private final AutomationPipelineMapper pipelineMapper;
    private final AutomationStageRunMapper stageMapper;
    private final AiModelMapper modelMapper;

    public Map<String, String> merge(RequirementDraftMergeRequest request) {
        String originalText = request == null || request.getOriginalText() == null
                ? "" : request.getOriginalText().trim();
        List<Long> attachmentIds = request == null || request.getAttachmentIds() == null
                ? List.of() : request.getAttachmentIds().stream().distinct().toList();
        List<String> sections = new ArrayList<>();
        if (!originalText.isBlank()) {
            sections.add("用户文字需求：\n" + originalText);
        }
        for (Long attachmentId : attachmentIds) {
            RequirementAttachment attachment = attachmentService.requireOwnedAttachment(attachmentId);
            RequirementParseTask task = attachmentService.latestSuccessfulTask(attachmentId);
            String result = task.getEditedResult() != null ? task.getEditedResult() : task.getRawResult();
            if (result != null && !result.isBlank()) {
                String label = "IMAGE".equals(attachment.getAttachmentType()) ? "图片" : "语音";
                sections.add(label + "「" + attachment.getOriginalFileName() + "」解析结果：\n" + result.trim());
            }
        }
        if (sections.isEmpty()) throw new IllegalArgumentException("请填写需求或等待附件解析完成");
        return Map.of("draftText", String.join("\n\n", sections), "mergedText", String.join("\n\n", sections));
    }

    public Map<String, Object> createPrd(RequirementPrdRequest request) {
        Long userId = requireUserId();
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("需求标题不能为空");
        }
        if (request.getRequirementText() == null || request.getRequirementText().isBlank()) {
            throw new IllegalArgumentException("确认后的需求内容不能为空");
        }
        if (request.getRequirementText().length() > 100_000) {
            throw new IllegalArgumentException("需求内容过长");
        }
        if (request.getRequestId() != null && !request.getRequestId().isBlank()) {
            // Ownership is checked through the filtered list; an empty list is valid for text-only requests.
            attachmentService.list(request.getRequestId());
        }

        AiModel chatModel = preferredChatModel();
        AutomationPipelineRequest pipelineRequest = new AutomationPipelineRequest();
        pipelineRequest.setProductLine("consumer");
        pipelineRequest.setProjectName(request.getTitle().trim());
        pipelineRequest.setRequirementTitle(request.getTitle().trim());
        pipelineRequest.setRequirementSummary(request.getRequirementText().trim());
        pipelineRequest.setInitiatorUserId(userId);
        pipelineRequest.setInitiatorUsername(TenantContext.username());
        pipelineRequest.setInitiator(TenantContext.username());
        pipelineRequest.setGenerateFrontend(false);
        pipelineRequest.setGenerateBackend(false);
        pipelineRequest.setCodeQualityEnabled(false);
        pipelineRequest.setAutoDeployEnabled(false);
        if (chatModel != null) {
            pipelineRequest.setModelId(chatModel.getId());
            pipelineRequest.setAiModelCode(chatModel.getModelCode());
        }
        AutomationPipeline pipeline = automationPipelineService.createPipeline(pipelineRequest);
        return Map.of("pipelineId", pipeline.getId(), "status", pipeline.getStatus());
    }

    public Map<String, Object> getPrd(Long pipelineId) {
        AutomationPipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline == null || !requireUserId().equals(pipeline.getInitiatorUserId())) {
            throw new IllegalArgumentException("PRD 不存在");
        }
        AutomationStageRun stage = stageMapper.selectOne(new LambdaQueryWrapper<AutomationStageRun>()
                .eq(AutomationStageRun::getPipelineId, pipelineId)
                .eq(AutomationStageRun::getStageKey, REQUIREMENT_STAGE)
                .orderByDesc(AutomationStageRun::getId)
                .last("LIMIT 1"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipelineId", pipelineId);
        result.put("pipelineStatus", pipeline.getStatus());
        result.put("status", stage == null ? "QUEUED" : stage.getStatus());
        result.put("content", stage == null || stage.getArtifactContent() == null ? "" : stage.getArtifactContent());
        result.put("errorMessage", stage == null || stage.getErrorMessage() == null ? "" : stage.getErrorMessage());
        return result;
    }

    private AiModel preferredChatModel() {
        AiModel model = modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getCapabilityType, ModelService.CAPABILITY_CHAT)
                .eq(AiModel::getDefaultForCapability, 1)
                .eq(AiModel::getStatus, 1)
                .orderByDesc(AiModel::getUpdateTime)
                .last("LIMIT 1"));
        if (model != null) return model;
        return modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getCapabilityType, ModelService.CAPABILITY_CHAT)
                .eq(AiModel::getStatus, 1)
                .orderByDesc(AiModel::getUpdateTime)
                .last("LIMIT 1"));
    }

    private Long requireUserId() {
        Long userId = TenantContext.userId();
        if (userId == null) throw new IllegalStateException("当前用户上下文不存在");
        return userId;
    }
}
