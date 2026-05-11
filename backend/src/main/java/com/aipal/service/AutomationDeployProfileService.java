package com.aipal.service;

import com.aipal.dto.AutomationDeployProfileRequest;
import com.aipal.dto.AutomationDeployProfileResponse;
import com.aipal.entity.AutomationDeployProfile;
import com.aipal.mapper.AutomationDeployProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AutomationDeployProfileService {
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final String MASK = "******";

    private final AutomationDeployProfileMapper deployProfileMapper;
    private final ObjectMapper objectMapper;

    public Page<AutomationDeployProfileResponse> list(int pageNum, int pageSize, Integer status) {
        LambdaQueryWrapper<AutomationDeployProfile> wrapper = new LambdaQueryWrapper<AutomationDeployProfile>()
                .eq(status != null, AutomationDeployProfile::getStatus, status)
                .orderByDesc(AutomationDeployProfile::getCreateTime);
        Page<AutomationDeployProfile> page = deployProfileMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<AutomationDeployProfileResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(profile -> toResponse(profile, true)).toList());
        return result;
    }

    public List<AutomationDeployProfileResponse> listEnabled() {
        return deployProfileMapper.selectList(new LambdaQueryWrapper<AutomationDeployProfile>()
                        .eq(AutomationDeployProfile::getStatus, STATUS_ENABLED)
                        .orderByDesc(AutomationDeployProfile::getCreateTime))
                .stream()
                .map(profile -> toResponse(profile, true))
                .toList();
    }

    public AutomationDeployProfileResponse get(Long id) {
        return toResponse(requireProfile(id), true);
    }

    public AutomationDeployProfileResponse create(AutomationDeployProfileRequest request) {
        AutomationDeployProfileRequest normalized = request == null ? new AutomationDeployProfileRequest() : request;
        validate(normalized);
        LocalDateTime now = LocalDateTime.now();
        AutomationDeployProfile profile = new AutomationDeployProfile();
        fill(profile, normalized, null);
        profile.setCreateTime(now);
        profile.setUpdateTime(now);
        profile.setIsDeleted(0);
        deployProfileMapper.insert(profile);
        return toResponse(profile, true);
    }

    public AutomationDeployProfileResponse update(Long id, AutomationDeployProfileRequest request) {
        AutomationDeployProfile profile = requireProfile(id);
        AutomationDeployProfileRequest normalized = request == null ? new AutomationDeployProfileRequest() : request;
        validate(normalized);
        fill(profile, normalized, profile);
        profile.setUpdateTime(LocalDateTime.now());
        deployProfileMapper.updateById(profile);
        return toResponse(profile, true);
    }

    public boolean delete(Long id) {
        return deployProfileMapper.deleteById(id) > 0;
    }

    public String requireEnabledSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        AutomationDeployProfile profile = requireProfile(id);
        if (profile.getStatus() == null || profile.getStatus() != STATUS_ENABLED) {
            throw new IllegalArgumentException("Deploy profile is disabled: " + id);
        }
        try {
            return objectMapper.writeValueAsString(toResponse(profile, false));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize deploy profile snapshot: " + e.getMessage(), e);
        }
    }

    public AutomationDeployProfileResponse readSnapshot(String snapshot) {
        if (isBlank(snapshot)) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshot, AutomationDeployProfileResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid deploy profile snapshot: " + e.getMessage(), e);
        }
    }

    private void fill(AutomationDeployProfile profile, AutomationDeployProfileRequest request, AutomationDeployProfile existing) {
        profile.setProfileName(request.getProfileName().trim());
        profile.setDeployType(normalizeDeployType(request.getDeployType()));
        profile.setEnvironmentName(isBlank(request.getEnvironmentName()) ? "dev" : request.getEnvironmentName().trim());
        profile.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        profile.setBuildCommand(blankToNull(request.getBuildCommand()));
        profile.setTestCommand(blankToNull(request.getTestCommand()));
        profile.setHealthCheckUrl(blankToNull(request.getHealthCheckUrl()));
        profile.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 600 : request.getTimeoutSeconds());
        profile.setDockerConfig(resolveConfigJson(request.getDockerConfig(), existing == null ? null : existing.getDockerConfig(), false));
        profile.setJenkinsConfig(resolveConfigJson(request.getJenkinsConfig(), existing == null ? null : existing.getJenkinsConfig(), true));
    }

    private void validate(AutomationDeployProfileRequest request) {
        if (isBlank(request.getProfileName())) {
            throw new IllegalArgumentException("profileName is required");
        }
        String deployType = normalizeDeployType(request.getDeployType());
        if (!"DOCKER".equals(deployType) && !"JENKINS".equals(deployType)) {
            throw new IllegalArgumentException("deployType must be DOCKER or JENKINS");
        }
        if (request.getStatus() != null && request.getStatus() != STATUS_ENABLED && request.getStatus() != STATUS_DISABLED) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
        if (request.getTimeoutSeconds() != null && (request.getTimeoutSeconds() < 10 || request.getTimeoutSeconds() > 7200)) {
            throw new IllegalArgumentException("timeoutSeconds must be between 10 and 7200");
        }
        assertJson(request.getDockerConfig(), "dockerConfig");
        assertJson(request.getJenkinsConfig(), "jenkinsConfig");
    }

    private String resolveConfigJson(String nextJson, String existingJson, boolean preserveSecrets) {
        if (isBlank(nextJson)) {
            return null;
        }
        if (!preserveSecrets || isBlank(existingJson)) {
            return nextJson.trim();
        }
        try {
            JsonNode next = objectMapper.readTree(nextJson);
            JsonNode existing = objectMapper.readTree(existingJson);
            if (next instanceof ObjectNode nextObject) {
                preserveSecret(nextObject, existing, "apiToken");
                preserveSecret(nextObject, existing, "buildToken");
            }
            return objectMapper.writeValueAsString(next);
        } catch (Exception e) {
            return nextJson.trim();
        }
    }

    private void preserveSecret(ObjectNode nextObject, JsonNode existing, String fieldName) {
        String value = nextObject.path(fieldName).asText("");
        if ((isBlank(value) || MASK.equals(value)) && existing != null && !isBlank(existing.path(fieldName).asText(""))) {
            nextObject.put(fieldName, existing.path(fieldName).asText(""));
        }
    }

    private AutomationDeployProfile requireProfile(Long id) {
        AutomationDeployProfile profile = deployProfileMapper.selectById(id);
        if (profile == null) {
            throw new IllegalArgumentException("Deploy profile does not exist: " + id);
        }
        return profile;
    }

    private AutomationDeployProfileResponse toResponse(AutomationDeployProfile profile, boolean maskSecrets) {
        AutomationDeployProfileResponse response = new AutomationDeployProfileResponse();
        response.setId(profile.getId());
        response.setProfileName(profile.getProfileName());
        response.setDeployType(profile.getDeployType());
        response.setEnvironmentName(profile.getEnvironmentName());
        response.setStatus(profile.getStatus());
        response.setBuildCommand(profile.getBuildCommand());
        response.setTestCommand(profile.getTestCommand());
        response.setHealthCheckUrl(profile.getHealthCheckUrl());
        response.setTimeoutSeconds(profile.getTimeoutSeconds());
        response.setDockerConfig(profile.getDockerConfig());
        response.setJenkinsConfig(maskSecrets ? maskJenkinsConfig(profile.getJenkinsConfig()) : profile.getJenkinsConfig());
        response.setCreateTime(profile.getCreateTime());
        response.setUpdateTime(profile.getUpdateTime());
        return response;
    }

    private String maskJenkinsConfig(String json) {
        if (isBlank(json)) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode objectNode) {
                if (!isBlank(objectNode.path("apiToken").asText(""))) {
                    objectNode.put("apiToken", MASK);
                }
                if (!isBlank(objectNode.path("buildToken").asText(""))) {
                    objectNode.put("buildToken", MASK);
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return json;
        }
    }

    private void assertJson(String value, String fieldName) {
        if (isBlank(value)) {
            return;
        }
        try {
            objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON");
        }
    }

    private String normalizeDeployType(String value) {
        return isBlank(value) ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
