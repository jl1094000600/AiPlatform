package com.aipal.service;

import com.aipal.entity.AiModel;
import com.aipal.mapper.AiModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelService {
    private final AiModelMapper modelMapper;

    public Page<AiModel> listModels(int pageNum, int pageSize) {
        Page<AiModel> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiModel::getCreateTime);
        return modelMapper.selectPage(page, wrapper);
    }

    public AiModel getModelById(Long id) {
        return modelMapper.selectById(id);
    }

    public boolean saveModel(AiModel model) {
        return modelMapper.insert(model) > 0;
    }

    public boolean updateModel(AiModel model) {
        return modelMapper.updateById(model) > 0;
    }

    public boolean deleteModel(Long id) {
        return modelMapper.deleteById(id) > 0;
    }
}
