package com.aipal.service;

import com.aipal.dto.ProjectWarRoomDtos;
import com.aipal.entity.AgentRun;
import com.aipal.entity.AiMemoryProject;
import com.aipal.entity.AutomationPipeline;
import com.aipal.entity.AutomationStageRun;
import com.aipal.mapper.AgentArtifactMapper;
import com.aipal.mapper.AgentMemorySnapshotMapper;
import com.aipal.mapper.AgentRunEventMapper;
import com.aipal.mapper.AutomationApprovalMapper;
import com.aipal.mapper.AutomationPipelineMapper;
import com.aipal.mapper.AutomationStageRunMapper;
import com.aipal.security.TenantContext;
import com.aipal.service.memory.MemoryProjectService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectWarRoomServiceTest {

    @Test
    void createsTeamAgentRunUsingPathProjectKey() {
        MemoryProjectService memoryProjectService = mock(MemoryProjectService.class);
        AgentRunService agentRunService = mock(AgentRunService.class);
        ProjectWarRoomService service = new ProjectWarRoomService(
                memoryProjectService,
                mock(AutomationPipelineMapper.class),
                mock(AutomationStageRunMapper.class),
                mock(AutomationApprovalMapper.class),
                agentRunService,
                mock(AgentRunEventMapper.class),
                mock(AgentArtifactMapper.class),
                mock(AgentMemorySnapshotMapper.class)
        );
        AiMemoryProject project = new AiMemoryProject();
        project.setProjectKey("trusted-project");
        when(memoryProjectService.requireAccessibleProject("trusted-project")).thenReturn(project);
        AgentRun run = new AgentRun();
        run.setId(9L);
        when(agentRunService.createOrReuse(any())).thenReturn(run);
        when(agentRunService.businessView(run)).thenReturn(new AgentRunService.RunView(9L, "trusted-project",
                "DELIVERY_REQUEST", "REQ-1", 3L, 4L, "QUEUED", "trace-1", null, 0,
                null, true, false, LocalDateTime.now(), null, null));

        service.createTeamAgentRun("trusted-project",
                new ProjectWarRoomDtos.TeamAgentRunRequest(3L, "DELIVERY_REQUEST", "REQ-1",
                        Map.of("projectKey", "body-project")));

        ArgumentCaptor<AgentRunService.CreateRunCommand> captor = ArgumentCaptor.forClass(AgentRunService.CreateRunCommand.class);
        verify(agentRunService).createOrReuse(captor.capture());
        assertEquals("trusted-project", captor.getValue().projectKey());
        assertEquals("DELIVERY_REQUEST", captor.getValue().businessType());
        assertEquals("REQ-1", captor.getValue().businessId());
    }

    @Test
    void warRoomDoesNotExposeStageArtifactPaths() {
        MemoryProjectService memoryProjectService = mock(MemoryProjectService.class);
        AutomationPipelineMapper pipelineMapper = mock(AutomationPipelineMapper.class);
        AutomationStageRunMapper stageRunMapper = mock(AutomationStageRunMapper.class);
        AutomationApprovalMapper approvalMapper = mock(AutomationApprovalMapper.class);
        AgentRunService agentRunService = mock(AgentRunService.class);
        ProjectWarRoomService service = new ProjectWarRoomService(
                memoryProjectService,
                pipelineMapper,
                stageRunMapper,
                approvalMapper,
                agentRunService,
                mock(AgentRunEventMapper.class),
                mock(AgentArtifactMapper.class),
                mock(AgentMemorySnapshotMapper.class)
        );
        AiMemoryProject project = new AiMemoryProject();
        project.setId(7L);
        project.setProjectKey("trusted-project");
        project.setProjectName("Trusted Project");
        project.setStatus("ACTIVE");
        when(memoryProjectService.requireAccessibleProject("trusted-project")).thenReturn(project);
        AutomationPipeline pipeline = new AutomationPipeline();
        pipeline.setId(11L);
        pipeline.setProjectKey("trusted-project");
        pipeline.setPipelineCode("AUTO_1");
        pipeline.setRequirementTitle("Release gate");
        pipeline.setStatus("RUNNING");
        pipeline.setCurrentStage("requirement_analysis");
        pipeline.setUpdateTime(LocalDateTime.now());
        when(pipelineMapper.selectList(any())).thenReturn(List.of(pipeline));
        AutomationStageRun stage = new AutomationStageRun();
        stage.setId(12L);
        stage.setStageKey("requirement_analysis");
        stage.setStatus("WAITING_APPROVAL");
        stage.setOutputSummary("PRD generated: marketDoc/generated-prd/secret.md");
        when(stageRunMapper.selectList(any())).thenReturn(List.of(stage));
        when(approvalMapper.selectList(any())).thenReturn(List.of());
        when(agentRunService.listByProjectKey("trusted-project", 20)).thenReturn(List.of());

        TenantContext.set(new TenantContext.Context(1L, "pm", 1L, "tenant", false, Set.of(), Set.of("automation:list")));
        try {
            ProjectWarRoomDtos.WarRoomResponse response = service.getWarRoom("trusted-project");

            String payload = response.toString();
            List.of("marketDoc", "secret.md", "artifactPath", "artifactContent", "storagePath",
                    "contentJson", "definitionSnapshot", "inputJson", "resultJson", "prompt",
                    "payload", "stack trace").forEach(token -> assertFalse(payload.contains(token), token));
        } finally {
            TenantContext.clear();
        }
    }
}
