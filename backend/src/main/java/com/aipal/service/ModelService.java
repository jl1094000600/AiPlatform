package com.aipal.service;

import com.aipal.entity.AiModel;
import com.aipal.mapper.AiModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ModelService {
    public static final String CAPABILITY_CHAT = "CHAT";
    public static final String CAPABILITY_VISION = "VISION";
    public static final String CAPABILITY_ASR = "ASR";
    private static final Set<String> SUPPORTED_CAPABILITIES =
            Set.of(CAPABILITY_CHAT, CAPABILITY_VISION, CAPABILITY_ASR);

    private final AiModelMapper modelMapper;

    public Page<AiModel> listModels(int pageNum, int pageSize) {
        Page<AiModel> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiModel::getCreateTime);
        Page<AiModel> result = modelMapper.selectPage(page, wrapper);
        result.getRecords().forEach(this::maskSecret);
        return result;
    }

    public AiModel getModelById(Long id) {
        AiModel model = modelMapper.selectById(id);
        maskSecret(model);
        return model;
    }

    @Transactional
    public boolean saveModel(AiModel model) {
        fillDefaults(model);
        model.setCreateTime(LocalDateTime.now());
        model.setUpdateTime(LocalDateTime.now());
        clearOtherDefaults(model);
        return modelMapper.insert(model) > 0;
    }

    @Transactional
    public boolean updateModel(AiModel model) {
        AiModel existing = modelMapper.selectById(model.getId());
        if (existing != null && (model.getApiKey() == null || model.getApiKey().isBlank()
                || "******".equals(model.getApiKey()))) {
            model.setApiKey(existing.getApiKey());
        }
        fillDefaults(model);
        model.setUpdateTime(LocalDateTime.now());
        clearOtherDefaults(model);
        return modelMapper.updateById(model) > 0;
    }

    public boolean deleteModel(Long id) {
        return modelMapper.deleteById(id) > 0;
    }

    public AiModel getDefaultEnabledModel(String capabilityType) {
        String normalizedCapability = normalizeCapability(capabilityType);
        return modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getCapabilityType, normalizedCapability)
                .eq(AiModel::getDefaultForCapability, 1)
                .eq(AiModel::getStatus, 1)
                .orderByDesc(AiModel::getUpdateTime)
                .last("LIMIT 1"));
    }

    private void fillDefaults(AiModel model) {
        if (model.getSdkType() == null || model.getSdkType().isBlank()) {
            model.setSdkType("openai-compatible");
        }
        if (model.getDefaultTemperature() == null) {
            model.setDefaultTemperature(BigDecimal.ONE);
        }
        if (model.getMaxTokens() == null) {
            model.setMaxTokens(4096);
        }
        if (model.getStatus() == null) {
            model.setStatus(1);
        }
        model.setCapabilityType(normalizeCapability(model.getCapabilityType()));
        model.setDefaultForCapability(Integer.valueOf(1).equals(model.getDefaultForCapability()) ? 1 : 0);
    }

    private String normalizeCapability(String capabilityType) {
        String normalized = capabilityType == null || capabilityType.isBlank()
                ? CAPABILITY_CHAT
                : capabilityType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CAPABILITIES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported model capability: " + capabilityType);
        }
        return normalized;
    }

    private void clearOtherDefaults(AiModel model) {
        if (!Integer.valueOf(1).equals(model.getDefaultForCapability())) {
            return;
        }
        LambdaUpdateWrapper<AiModel> wrapper = new LambdaUpdateWrapper<AiModel>()
                .eq(AiModel::getCapabilityType, model.getCapabilityType())
                .eq(AiModel::getDefaultForCapability, 1)
                .set(AiModel::getDefaultForCapability, 0);
        if (model.getId() != null) {
            wrapper.ne(AiModel::getId, model.getId());
        }
        modelMapper.update(null, wrapper);
    }

    private void maskSecret(AiModel model) {
        if (model != null && model.getApiKey() != null && !model.getApiKey().isBlank()) {
            model.setApiKey("******");
        }
    }
}
