package com.aipal.service;

import com.aipal.dto.AutomationPipelineRequest;
import com.aipal.entity.AiModel;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.AutomationApprovalMapper;
import com.aipal.mapper.AutomationGenerationJobMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AutomationPipelineServiceTest {

    @Test
    void createsPipelineWithSevenStandardStages() {
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AutomationGenerationJobMapper generationJobMapper = mock(AutomationGenerationJobMapper.class);
        AiModelMapper modelMapper = mock(AiModelMapper.class);
        List<AutomationStageRun> insertedStages = new ArrayList<>();

        doAnswer(invocation -> {
            AutomationPipeline pipeline = invocation.getArgument(0);
            pipeline.setId(1L);
            return 1;
        }).when(pipelineMapper).insert(any());
        doAnswer(invocation -> {
            AutomationStageRun stage = invocation.getArgument(0);
            insertedStages.add(stage);
            return 1;
        }).when(stageRunMapper).insert(any());

        AutomationPipelineService service = new AutomationPipelineService(pipelineMapper, stageRunMapper, approvalMapper, generationJobMapper, modelMapper);
        AutomationPipelineRequest request = new AutomationPipelineRequest();
        request.setProductLine("Core");
        request.setProjectName("AI Platform");
        request.setRequirementTitle("Automated delivery");

        AutomationPipeline pipeline = service.createPipeline(request);

        assertNotNull(pipeline.getPipelineCode());
        assertEquals(7, insertedStages.size());
        assertEquals("requirement_analysis", insertedStages.get(0).getStageKey());
        assertEquals("QUEUED", insertedStages.get(0).getStatus());
        assertEquals("delivery_report", insertedStages.get(6).getStageKey());
        verify(stageRunMapper, times(7)).insert(any());
        verify(generationJobMapper, times(1)).insert(any());
    }

    @Test
    void capsMiniMaxMaxTokensAtProviderLimit() {
        AutomationPipelineService service = new AutomationPipelineService(
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                mock(AutomationGenerationJobMapper.class),
                mock(AiModelMapper.class)
        );
        AiModel model = new AiModel();
        model.setProvider("MiniMax");
        model.setMaxTokens(200000);

        assertEquals(8192, service.resolveMaxTokens(model));
    }
}
