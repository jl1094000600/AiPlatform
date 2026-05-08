package com.aipal.service;

import com.aipal.entity.AiAgentVersion;
import com.aipal.mapper.AiAgentMapper;
import com.aipal.mapper.AiAgentVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentVersionService {

    private final AiAgentVersionMapper versionMapper;
    private final AiAgentMapper agentMapper;

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

    @Transactional
    public boolean rollbackToPreviousVersion(Long agentId) {
        LambdaQueryWrapper<AiAgentVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAgentVersion::getAgentId, agentId)
                .orderByDesc(AiAgentVersion::getPublishTime)
                .orderByDesc(AiAgentVersion::getCreateTime);
        List<AiAgentVersion> versions = versionMapper.selectList(wrapper);
        if (versions.size() < 2) {
            return false;
        }

        AiAgentVersion current = versions.get(0);
        AiAgentVersion previous = versions.get(1);

        AiAgentVersion currentUpdate = new AiAgentVersion();
        currentUpdate.setId(current.getId());
        currentUpdate.setStatus(0);
        versionMapper.updateById(currentUpdate);

        AiAgentVersion previousUpdate = new AiAgentVersion();
        previousUpdate.setId(previous.getId());
        previousUpdate.setStatus(1);
        versionMapper.updateById(previousUpdate);
        return agentMapper.selectById(agentId) != null;
    }
}
