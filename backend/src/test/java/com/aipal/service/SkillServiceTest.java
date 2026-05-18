package com.aipal.service;

import com.aipal.dto.SkillFunctionDefinition;
import com.aipal.dto.SkillGenerateRequest;
import com.aipal.dto.SkillRequest;
import com.aipal.dto.SkillResponse;
import com.aipal.entity.AiSkill;
import com.aipal.mapper.AiSkillMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillServiceTest {

    @Test
    void createsSkillWithFunctionDefinitions() {
        AiSkillMapper mapper = mock(AiSkillMapper.class);
        doAnswer(invocation -> {
            AiSkill skill = invocation.getArgument(0);
            skill.setId(1L);
            return 1;
        }).when(mapper).insert(any());
        SkillService service = new SkillService(mapper);

        SkillResponse response = service.createSkill(request("Code Review Skill", "{\"type\":\"object\"}"));

        assertEquals(1L, response.getId());
        assertNotNull(response.getSkillCode());
        assertEquals("Code Review Skill", response.getSkillName());
        assertEquals(1, response.getFunctionDefinitions().size());
        verify(mapper).insert(any());
    }

    @Test
    void rejectsInvalidFunctionParameterJson() {
        SkillService service = new SkillService(mock(AiSkillMapper.class));

        SkillRequest request = request("Bad Skill", "{");

        assertThrows(IllegalArgumentException.class, () -> service.createSkill(request));
    }

    @Test
    void rejectsEnabledFunctionWithoutName() {
        SkillService service = new SkillService(mock(AiSkillMapper.class));
        SkillRequest request = new SkillRequest();
        request.setSkillName("Bad Function");
        SkillFunctionDefinition function = new SkillFunctionDefinition();
        function.setEnabled(true);
        request.setFunctionDefinitions(List.of(function));

        assertThrows(IllegalArgumentException.class, () -> service.createSkill(request));
    }

    @Test
    void returnsEnabledSkillSnapshot() {
        AiSkillMapper mapper = mock(AiSkillMapper.class);
        AiSkill skill = skill("SKILL_TEST", "Test Skill", 1);
        when(mapper.selectById(3L)).thenReturn(skill);
        SkillService service = new SkillService(mapper);

        String snapshot = service.requireEnabledSkillSnapshot(3L);

        assertEquals(true, snapshot.contains("Test Skill"));
        assertEquals(true, snapshot.contains("functionDefinitions"));
    }

    @Test
    void rejectsDisabledSkillSnapshot() {
        AiSkillMapper mapper = mock(AiSkillMapper.class);
        when(mapper.selectById(3L)).thenReturn(skill("SKILL_TEST", "Test Skill", 0));
        SkillService service = new SkillService(mapper);

        assertThrows(IllegalArgumentException.class, () -> service.requireEnabledSkillSnapshot(3L));
    }

    @Test
    void generatesSkillDraftFromRequirement() {
        SkillService service = new SkillService(mock(AiSkillMapper.class));
        SkillGenerateRequest request = new SkillGenerateRequest();
        request.setRequirement("生成客户拜访纪要");
        request.setScenario("销售跟进");
        request.setIncludeFunction(true);

        SkillRequest draft = service.generateSkillDraft(request);

        assertEquals(1, draft.getStatus());
        assertEquals(true, draft.getSkillCode().startsWith("AI_SKILL_"));
        assertEquals(true, draft.getSkillName().contains("Skill"));
        assertEquals(true, draft.getPromptContent().contains("生成客户拜访纪要"));
        assertEquals(1, draft.getFunctionDefinitions().size());
        assertEquals("generateSkillContent", draft.getFunctionDefinitions().get(0).getName());
    }

    @Test
    void rejectsBlankSkillDraftRequirement() {
        SkillService service = new SkillService(mock(AiSkillMapper.class));
        SkillGenerateRequest request = new SkillGenerateRequest();
        request.setRequirement(" ");

        assertThrows(IllegalArgumentException.class, () -> service.generateSkillDraft(request));
    }

    private SkillRequest request(String name, String parametersJson) {
        SkillRequest request = new SkillRequest();
        request.setSkillName(name);
        request.setPromptContent("Follow platform conventions.");
        SkillFunctionDefinition function = new SkillFunctionDefinition();
        function.setName("readCustomer");
        function.setDescription("Read customer by id");
        function.setParametersJson(parametersJson);
        function.setReturnSchema("{\"type\":\"object\"}");
        function.setJavaSnippet("Customer readCustomer(Long id)");
        function.setEnabled(true);
        request.setFunctionDefinitions(List.of(function));
        return request;
    }

    private AiSkill skill(String code, String name, int status) {
        AiSkill skill = new AiSkill();
        skill.setId(3L);
        skill.setSkillCode(code);
        skill.setSkillName(name);
        skill.setStatus(status);
        skill.setFunctionDefinitions("[]");
        return skill;
    }
}
