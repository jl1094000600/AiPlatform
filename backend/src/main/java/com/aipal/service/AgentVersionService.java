package com.aipal.service;

import com.aipal.entity.AiAgentVersion;
import com.aipal.mapper.AiAgentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentVersionService {

    private final AiAgentVersionMapper versionMapper;

    public List<AiAgentVersion> getVersionsByAgentId(Long agentId) {
        LambdaQueryWrapper<AiAgentVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAgentVersion::getAgentId, agentId)
               .orderByDesc(AiAgentVersion::getCreateTime);
        return versionMapper.selectList(wrapper);
    }

    public AiAgentVersion getLatestVersion(Long agentId) {
        LambdaQueryWrapper<AiAgentVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAgentVersion::getAgentId, agentId)
               .eq(AiAgentVersion::getStatus, 1)
               .orderByDesc(AiAgentVersion::getCreateTime)
               .last("LIMIT 1");
        return versionMapper.selectOne(wrapper);
    }

    public boolean saveVersion(AiAgentVersion version) {
        return versionMapper.insert(version) > 0;
    }

    public boolean publishVersion(Long versionId) {
        AiAgentVersion version = new AiAgentVersion();
        version.setId(versionId);
        version.setStatus(1);
        return versionMapper.updateById(version) > 0;
    }
}
