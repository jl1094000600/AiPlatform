package com.aipal.service;

import com.aipal.dto.AgentAuthRequest;
import com.aipal.entity.BizAgentAuth;
import com.aipal.mapper.BizAgentAuthMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BizAgentAuthService {

    private final BizAgentAuthMapper bizAgentAuthMapper;

    public List<BizAgentAuth> getAuthsByModuleId(Long moduleId) {
        LambdaQueryWrapper<BizAgentAuth> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizAgentAuth::getBizModuleId, moduleId);
        return bizAgentAuthMapper.selectList(wrapper);
    }

    public boolean authorizeAgent(AgentAuthRequest request) {
        BizAgentAuth auth = new BizAgentAuth();
        auth.setBizModuleId(request.getBizModuleId());
        auth.setAgentId(request.getAgentId());
        auth.setAgentVersion(request.getAgentVersion());
        auth.setQpsLimit(request.getQpsLimit() != null ? request.getQpsLimit() : 10);
        auth.setDailyLimit(request.getDailyLimit() != null ? request.getDailyLimit() : 10000);
        auth.setStatus(1);
        return bizAgentAuthMapper.insert(auth) > 0;
    }

    public boolean updateAgentAuth(Long authId, AgentAuthRequest request) {
        BizAgentAuth auth = new BizAgentAuth();
        auth.setId(authId);
        auth.setAgentVersion(request.getAgentVersion());
        auth.setQpsLimit(request.getQpsLimit());
        auth.setDailyLimit(request.getDailyLimit());
        if (request.getStatus() != null) {
            auth.setStatus(request.getStatus());
        }
        return bizAgentAuthMapper.updateById(auth) > 0;
    }

    public boolean deleteAgentAuth(Long authId) {
        return bizAgentAuthMapper.deleteById(authId) > 0;
    }
}
