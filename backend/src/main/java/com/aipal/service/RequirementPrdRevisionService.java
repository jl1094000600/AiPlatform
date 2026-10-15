package com.aipal.service;

import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SC03: acceptance criteria on PRD artifacts, content hash, versioned confirm/reject.
 * Confirmed PRD is required before code generation; changes invalidate old approvals.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RequirementPrdRevisionService {

    private static final String STAGE_REQUIREMENT = "requirement_analysis";

    private final AutomationPipelineMapper pipelineMapper;
    private final AutomationStageRunMapper stageRunMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record AcceptanceCriterionInput(String stableId, String priority, String preconditions,
                                           String inputs, String expected, String negativeCases,
                                           Boolean required) {
    }

    public Map<String, Object> saveAcceptanceCriteria(Long pipelineId, List<AcceptanceCriterionInput> criteria) {
        AutomationStageRun stage = requireStage(pipelineId);
        List<Map<String, Object>> normalized = normalize(criteria);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one acceptance criterion is required");
        }
        Map<String, Object> doc = readDoc(stage);
        // F06: editing AC creates a new revision and invalidates prior approval.
        int version = doc.containsKey("version") ? ((Number) doc.get("version")).intValue() : 1;
        doc.put("version", version + 1);
        doc.put("approved", false);
        doc.put("approvalInvalidatedAt", LocalDateTime.now().toString());
        doc.put("acceptanceCriteria", normalized);
        doc.put("acUpdatedAt", LocalDateTime.now().toString());
        // Stable hash over canonical body + AC rows only (not whole prior artifact JSON).
        doc.put("contentHash", contentHash(String.valueOf(doc.getOrDefault("prdMarkdown", "")), normalized));
        writeDoc(stage, doc);
        // Downstream evidence cannot reuse prior SUCCESS approval for edited AC.
        if ("SUCCESS".equals(stage.getStatus())) {
            stage.setStatus("WAITING_APPROVAL");
            stage.setUpdateTime(LocalDateTime.now());
            stageRunMapper.updateById(stage);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipelineId", pipelineId);
        result.put("version", ((Number) doc.get("version")).intValue());
        result.put("contentHash", doc.get("contentHash"));
        result.put("approved", false);
        result.put("acceptanceCriteria", normalized);
        return result;
    }

    @Transactional
    public Map<String, Object> confirmPrd(Long pipelineId, Integer expectedVersion, boolean approved,
                                          String reason) {
        AutomationStageRun stage = requireStage(pipelineId);
        Map<String, Object> doc = readDoc(stage);
        int current = doc.containsKey("version") ? ((Number) doc.get("version")).intValue() : 1;
        if (expectedVersion == null) {
            throw new IllegalStateException("VALIDATION_FAILED: expectedVersion is required");
        }
        if (expectedVersion != current) {
            throw new IllegalStateException("VERSION_CONFLICT: expected " + expectedVersion + " but was " + current);
        }
        List<?> acs = doc.get("acceptanceCriteria") instanceof List<?> list ? list : List.of();
        if (approved && acs.isEmpty()) {
            throw new IllegalStateException("VALIDATION_FAILED: acceptance criteria required before confirm");
        }
        // Re-apply version in doc before write so hash/stage stay coherent (F06).
        if (approved) {
            stage.setStatus("SUCCESS");
            stage.setErrorMessage(null);
        } else {
            stage.setStatus("REJECTED");
            stage.setErrorMessage(reason == null || reason.isBlank() ? "PRD rejected" : reason);
        }
        doc.put("version", current + 1);
        doc.put("approved", approved);
        doc.put("confirmedAt", LocalDateTime.now().toString());
        writeDoc(stage, doc);

        AutomationPipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline != null) {
            if (!approved) {
                pipeline.setStatus("BLOCKED");
            }
            pipeline.setUpdateTime(LocalDateTime.now());
            pipelineMapper.updateById(pipeline);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipelineId", pipelineId);
        result.put("approved", approved);
        result.put("status", stage.getStatus());
        result.put("version", current + 1);
        result.put("contentHash", doc.get("contentHash"));
        result.put("acceptanceCriteria", acs);
        return result;
    }

    public Map<String, Object> getRevision(Long pipelineId) {
        AutomationStageRun stage = requireStage(pipelineId);
        Map<String, Object> doc = readDoc(stage);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipelineId", pipelineId);
        result.put("status", stage.getStatus());
        int version = doc.containsKey("version") ? ((Number) doc.get("version")).intValue() : 1;
        result.put("version", version);
        result.put("contentHash", doc.getOrDefault("contentHash",
                contentHash(stage.getArtifactContent(), List.of())));
        result.put("acceptanceCriteria", doc.getOrDefault("acceptanceCriteria", List.of()));
        result.put("content", stage.getArtifactContent() == null ? "" : stage.getArtifactContent());
        return result;
    }

    private List<Map<String, Object>> normalize(List<AcceptanceCriterionInput> criteria) {
        if (criteria == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AcceptanceCriterionInput item : criteria) {
            if (item == null) {
                continue;
            }
            if (item.stableId() == null || item.stableId().isBlank()) {
                throw new IllegalArgumentException("stableId is required for each AC");
            }
            if (item.expected() == null || item.expected().isBlank()) {
                throw new IllegalArgumentException("expected is required for AC " + item.stableId());
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stableId", item.stableId().trim());
            row.put("priority", item.priority() == null ? "P1" : item.priority());
            row.put("preconditions", item.preconditions() == null ? "" : item.preconditions());
            row.put("inputs", item.inputs() == null ? "" : item.inputs());
            row.put("expected", item.expected());
            row.put("negativeCases", item.negativeCases() == null ? "" : item.negativeCases());
            row.put("required", item.required() == null || item.required());
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDoc(AutomationStageRun stage) {
        String json = stage.getArtifactContent();
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            // PRD is often Markdown; wrap into doc if not JSON
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return new LinkedHashMap<>(map);
        } catch (Exception ignored) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("prdMarkdown", json);
            return doc;
        }
    }

    private void writeDoc(AutomationStageRun stage, Map<String, Object> doc) {
        try {
            if (doc.containsKey("prdMarkdown") && doc.size() == 1) {
                stage.setArtifactContent(String.valueOf(doc.get("prdMarkdown")));
            } else {
                // Preserve markdown body alongside structured AC metadata
                Object md = doc.remove("prdMarkdown");
                if (md != null) {
                    doc.put("prdMarkdown", md);
                }
                stage.setArtifactContent(objectMapper.writeValueAsString(doc));
            }
            stage.setUpdateTime(LocalDateTime.now());
            stageRunMapper.updateById(stage);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist PRD revision: " + e.getMessage(), e);
        }
    }

    private AutomationStageRun requireStage(Long pipelineId) {
        AutomationStageRun stage = stageRunMapper.selectOne(new LambdaQueryWrapper<AutomationStageRun>()
                .eq(AutomationStageRun::getPipelineId, pipelineId)
                .eq(AutomationStageRun::getStageKey, STAGE_REQUIREMENT)
                .orderByDesc(AutomationStageRun::getId)
                .last("LIMIT 1"));
        if (stage == null) {
            throw new IllegalArgumentException("PRD stage not found for pipeline " + pipelineId);
        }
        return stage;
    }

    private String contentHash(String prdContent, List<Map<String, Object>> criteria) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((prdContent == null ? "" : prdContent).getBytes(StandardCharsets.UTF_8));
            digest.update(objectMapper.writeValueAsString(criteria == null ? List.of() : criteria)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("content hash failed: " + e.getMessage(), e);
        }
    }
}
