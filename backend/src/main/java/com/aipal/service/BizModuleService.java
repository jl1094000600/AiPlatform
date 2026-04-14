package com.aipal.service;

import com.aipal.entity.BizModule;
import com.aipal.mapper.BizModuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BizModuleService {

    private final BizModuleMapper bizModuleMapper;

    public Page<BizModule> listBizModules(int pageNum, int pageSize) {
        Page<BizModule> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BizModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizModule::getCreateTime);
        return bizModuleMapper.selectPage(page, wrapper);
    }

    public BizModule getBizModuleById(Long id) {
        return bizModuleMapper.selectById(id);
    }

    public boolean saveBizModule(BizModule bizModule) {
        return bizModuleMapper.insert(bizModule) > 0;
    }

    public boolean updateBizModule(BizModule bizModule) {
        return bizModuleMapper.updateById(bizModule) > 0;
    }

    public boolean deleteBizModule(Long id) {
        return bizModuleMapper.deleteById(id) > 0;
    }
}
