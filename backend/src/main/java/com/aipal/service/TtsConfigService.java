package com.aipal.service;

import com.aipal.entity.AiTtsConfig;
import com.aipal.mapper.AiTtsConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsConfigService {

    private final AiTtsConfigMapper ttsConfigMapper;

    public List<AiTtsConfig> getAllConfigs() {
        return ttsConfigMapper.selectList(
            new LambdaQueryWrapper<AiTtsConfig>()
                .eq(AiTtsConfig::getStatus, 1)
                .orderByDesc(AiTtsConfig::getCreateTime)
        );
    }

    @Cacheable(value = "ttsConfig", key = "#key")
    public AiTtsConfig getConfigByKey(String key) {
        return ttsConfigMapper.selectOne(
            new LambdaQueryWrapper<AiTtsConfig>()
                .eq(AiTtsConfig::getConfigKey, key)
                .eq(AiTtsConfig::getStatus, 1)
        );
    }

    @CacheEvict(value = "ttsConfig", key = "#config.configKey")
    public boolean saveConfig(AiTtsConfig config) {
        if (config.getId() == null) {
            config.setCreateTime(LocalDateTime.now());
            return ttsConfigMapper.insert(config) > 0;
        } else {
            config.setUpdateTime(LocalDateTime.now());
            return ttsConfigMapper.updateById(config) > 0;
        }
    }

    @CacheEvict(value = "ttsConfig", key = "#key")
    public boolean deleteConfig(String key) {
        AiTtsConfig config = getConfigByKey(key);
        if (config != null) {
            config.setIsDeleted(1);
            config.setUpdateTime(LocalDateTime.now());
            return ttsConfigMapper.updateById(config) > 0;
        }
        return false;
    }
}
