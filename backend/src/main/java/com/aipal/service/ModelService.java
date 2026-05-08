package com.aipal.service;

import com.aipal.entity.AiModel;
import com.aipal.mapper.AiModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ModelService {
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

    public boolean saveModel(AiModel model) {
        fillDefaults(model);
        model.setCreateTime(LocalDateTime.now());
        model.setUpdateTime(LocalDateTime.now());
        return modelMapper.insert(model) > 0;
    }

    public boolean updateModel(AiModel model) {
        AiModel existing = modelMapper.selectById(model.getId());
        if (existing != null && (model.getApiKey() == null || model.getApiKey().isBlank()
                || "******".equals(model.getApiKey()))) {
            model.setApiKey(existing.getApiKey());
        }
        fillDefaults(model);
        model.setUpdateTime(LocalDateTime.now());
        return modelMapper.updateById(model) > 0;
    }

    public boolean deleteModel(Long id) {
        return modelMapper.deleteById(id) > 0;
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
    }

    private void maskSecret(AiModel model) {
        if (model != null && model.getApiKey() != null && !model.getApiKey().isBlank()) {
            model.setApiKey("******");
        }
    }
}
