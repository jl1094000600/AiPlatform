package com.aipal.service;

import com.aipal.dto.SkillFunctionDefinition;
import com.aipal.dto.SkillRequest;
import com.aipal.dto.SkillResponse;
import com.aipal.entity.AiSkill;
import com.aipal.mapper.AiSkillMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SkillService {
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    private final AiSkillMapper skillMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Page<SkillResponse> listSkills(int pageNum, int pageSize, Integer status) {
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<AiSkill>()
                .eq(status != null, AiSkill::getStatus, status)
                .orderByDesc(AiSkill::getCreateTime);
        Page<AiSkill> page = skillMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<SkillResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toResponse).toList());
        return result;
    }

    public List<SkillResponse> listEnabledSkills() {
        return skillMapper.selectList(new LambdaQueryWrapper<AiSkill>()
                        .eq(AiSkill::getStatus, STATUS_ENABLED)
                        .orderByDesc(AiSkill::getCreateTime))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SkillResponse getSkill(Long id) {
        return toResponse(requireSkill(id));
    }

    public SkillResponse createSkill(SkillRequest request) {
        SkillRequest normalized = request == null ? new SkillRequest() : request;
        validate(normalized);
        LocalDateTime now = LocalDateTime.now();
        AiSkill skill = new AiSkill();
        fillSkill(skill, normalized);
        skill.setCreateTime(now);
        skill.setUpdateTime(now);
        skill.setIsDeleted(0);
        skillMapper.insert(skill);
        return toResponse(skill);
    }

    public SkillResponse updateSkill(Long id, SkillRequest request) {
        AiSkill skill = requireSkill(id);
        SkillRequest normalized = request == null ? new SkillRequest() : request;
        validate(normalized);
        fillSkill(skill, normalized);
        skill.setUpdateTime(LocalDateTime.now());
        skillMapper.updateById(skill);
        return toResponse(skill);
    }

    public boolean deleteSkill(Long id) {
        return skillMapper.deleteById(id) > 0;
    }

    public String requireEnabledSkillSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        AiSkill skill = requireSkill(id);
        if (skill.getStatus() == null || skill.getStatus() != STATUS_ENABLED) {
            throw new IllegalArgumentException("Skill is disabled: " + id);
        }
        try {
            return objectMapper.writeValueAsString(toResponse(skill));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize skill snapshot: " + e.getMessage(), e);
        }
    }

    private void fillSkill(AiSkill skill, SkillRequest request) {
        skill.setSkillCode(resolveSkillCode(request.getSkillCode()));
        skill.setSkillName(request.getSkillName().trim());
        skill.setDescription(blankToNull(request.getDescription()));
        skill.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        skill.setPromptContent(blankToNull(request.getPromptContent()));
        skill.setFunctionDefinitions(writeFunctions(request.getFunctionDefinitions()));
    }

    private void validate(SkillRequest request) {
        if (request.getSkillName() == null || request.getSkillName().isBlank()) {
            throw new IllegalArgumentException("skillName is required");
        }
        if (request.getStatus() != null && request.getStatus() != STATUS_ENABLED && request.getStatus() != STATUS_DISABLED) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
        List<SkillFunctionDefinition> functions = request.getFunctionDefinitions() == null
                ? List.of() : request.getFunctionDefinitions();
        for (SkillFunctionDefinition function : functions) {
            boolean enabled = function.getEnabled() == null || function.getEnabled();
            if (enabled && (function.getName() == null || function.getName().isBlank())) {
                throw new IllegalArgumentException("Enabled function name is required");
            }
            if (function.getParametersJson() != null && !function.getParametersJson().isBlank()) {
                assertJson(function.getParametersJson(), "parametersJson");
            }
        }
    }

    private AiSkill requireSkill(Long id) {
        AiSkill skill = skillMapper.selectById(id);
        if (skill == null) {
            throw new IllegalArgumentException("Skill does not exist: " + id);
        }
        return skill;
    }

    private SkillResponse toResponse(AiSkill skill) {
        if (skill == null) {
            return null;
        }
        SkillResponse response = new SkillResponse();
        response.setId(skill.getId());
        response.setSkillCode(skill.getSkillCode());
        response.setSkillName(skill.getSkillName());
        response.setDescription(skill.getDescription());
        response.setStatus(skill.getStatus());
        response.setPromptContent(skill.getPromptContent());
        response.setFunctionDefinitions(readFunctions(skill.getFunctionDefinitions()));
        response.setCreateTime(skill.getCreateTime());
        response.setUpdateTime(skill.getUpdateTime());
        return response;
    }

    private String writeFunctions(List<SkillFunctionDefinition> functions) {
        try {
            return objectMapper.writeValueAsString(functions == null ? List.of() : functions);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid function definitions: " + e.getMessage(), e);
        }
    }

    private List<SkillFunctionDefinition> readFunctions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SkillFunctionDefinition>>() {});
        } catch (IOException e) {
            return List.of();
        }
    }

    private void assertJson(String value, String fieldName) {
        try {
            objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON");
        }
    }

    private String resolveSkillCode(String value) {
        if (value == null || value.isBlank()) {
            return "SKILL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
