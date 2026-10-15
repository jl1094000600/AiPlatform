package com.aipal.service;

import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequirementPrdRevisionServiceTest {

    private final AutomationStageRunMapper stageMapper = mock(AutomationStageRunMapper.class);
    private final AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
    private final RequirementPrdRevisionService service =
            new RequirementPrdRevisionService(pipelineMapper, stageMapper);

    private AutomationStageRun stage(String content) {
        AutomationStageRun stage = new AutomationStageRun();
        stage.setId(10L);
        stage.setPipelineId(1L);
        stage.setStageKey("requirement_analysis");
        stage.setArtifactContent(content);
        stage.setStatus("WAITING_APPROVAL");
        return stage;
    }

    @Test
    void confirmRequiresAcceptanceCriteria() {
        when(stageMapper.selectOne(any())).thenReturn(stage("# draft"));
        assertThrows(IllegalStateException.class, () -> service.confirmPrd(1L, 1, true, null));
    }

    @Test
    void saveAcThenConfirmApprovesAndBumpsVersion() throws Exception {
        AutomationStageRun stage = stage("# draft PRD");
        when(stageMapper.selectOne(any())).thenReturn(stage);
        when(stageMapper.updateById(any())).thenReturn(1);
        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setId(1L);
        when(pipelineMapper.selectById(1L)).thenReturn(pipeline);

        var saved = service.saveAcceptanceCriteria(1L, List.of(
                new RequirementPrdRevisionService.AcceptanceCriterionInput(
                        "AC-001", "P0", "user logged in", "create order", "order created", "rejects empty cart", true)));
        assertTrue(String.valueOf(saved.get("contentHash")).length() == 64);

        var confirmed = service.confirmPrd(1L, 1, true, null);
        assertEquals(true, confirmed.get("approved"));
        assertEquals(2, confirmed.get("version"));
        assertEquals("SUCCESS", confirmed.get("status"));
    }

    @Test
    void versionConflictRejected() {
        when(stageMapper.selectOne(any())).thenReturn(stage("# draft"));
        assertThrows(IllegalStateException.class, () -> service.confirmPrd(1L, 99, true, null));
    }

    @Test
    void rejectMarksStageRejected() {
        AutomationStageRun stage = stage("# draft");
        when(stageMapper.selectOne(any())).thenReturn(stage);
        when(stageMapper.updateById(any())).thenReturn(1);
        when(pipelineMapper.selectById(1L)).thenReturn(new AutomationPipeline());
        var result = service.confirmPrd(1L, null, false, "needs rewrite");
        assertEquals("REJECTED", result.get("status"));
        assertEquals("needs rewrite", stage.getErrorMessage());
    }
}
