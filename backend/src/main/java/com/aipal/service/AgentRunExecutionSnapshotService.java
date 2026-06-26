package com.aipal.service;

import com.aipal.entity.AgentRun;
import com.aipal.entity.AgentRunExecutionSnapshot;
import com.aipal.entity.AiAgent;
import com.aipal.entity.AiAgentRuntimeConfig;
import com.aipal.entity.AiAgentVersion;
import com.aipal.mapper.AgentRunExecutionSnapshotMapper;
import com.aipal.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Private encrypted execution definition; no controller may expose this service's plaintext. */
@Service
@RequiredArgsConstructor
public class AgentRunExecutionSnapshotService {
    public static final String FORMAT = "execution-snapshot-v2";
    private final AgentRunExecutionSnapshotMapper snapshotMapper;
    private final AgentRunSnapshotCrypto crypto;
    private final ObjectMapper objectMapper;

    public void create(AgentRun run, AiAgent agent, AiAgentVersion version, AiAgentRuntimeConfig runtime) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("agentId", agent.getId());
        root.put("agentVersionId", version.getId());
        root.put("agentVersion", version.getVersion());
        root.put("agentVersionConfig", version.getConfig());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("modelId", runtime.getModelId()); config.put("datasetId", runtime.getDatasetId());
        config.put("topK", runtime.getTopK()); config.put("temperature", runtime.getTemperature());
        config.put("promptId", runtime.getPromptId()); config.put("promptVersionId", runtime.getPromptVersionId());
        config.put("systemPrompt", runtime.getSystemPrompt()); config.put("userPromptTemplate", runtime.getUserPromptTemplate());
        root.put("runtimeConfig", config);
        try {
            String plaintext = objectMapper.writeValueAsString(root);
            AgentRunSnapshotCrypto.EncryptedSnapshot encrypted = crypto.encrypt(plaintext, aad(run));
            AgentRunExecutionSnapshot entity = new AgentRunExecutionSnapshot();
            entity.setTenantId(run.getTenantId()); entity.setRunId(run.getId()); entity.setSnapshotFormat(FORMAT);
            entity.setKeyId(encrypted.keyId()); entity.setIvB64(encrypted.ivB64()); entity.setCiphertextB64(encrypted.ciphertextB64());
            entity.setPlaintextHash(sha256(plaintext)); entity.setCreateTime(LocalDateTime.now()); entity.setIsDeleted(0);
            snapshotMapper.insert(entity);
        } catch (Exception e) { throw new IllegalStateException("Unable to create encrypted execution snapshot", e); }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> decrypt(AgentRun run) {
        AgentRunExecutionSnapshot entity = snapshotMapper.selectOne(new LambdaQueryWrapper<AgentRunExecutionSnapshot>()
                .eq(AgentRunExecutionSnapshot::getTenantId, TenantContext.tenantId()).eq(AgentRunExecutionSnapshot::getRunId, run.getId()).last("LIMIT 1"));
        if (entity == null || !FORMAT.equals(entity.getSnapshotFormat())) throw new IllegalStateException("Execution snapshot is unavailable");
        String plaintext = crypto.decrypt(new AgentRunSnapshotCrypto.EncryptedSnapshot(entity.getKeyId(), entity.getIvB64(), entity.getCiphertextB64()), aad(run));
        if (!sha256(plaintext).equals(entity.getPlaintextHash())) throw new IllegalStateException("Execution snapshot hash mismatch");
        try { return objectMapper.readValue(plaintext, Map.class); }
        catch (Exception e) { throw new IllegalStateException("Execution snapshot cannot be decoded", e); }
    }

    private String aad(AgentRun run) { return run.getTenantId() + "|" + run.getProjectKey() + "|" + run.getIdempotencyKey() + "|" + run.getAgentVersionId() + "|" + run.getDefinitionHash() + "|" + FORMAT; }
    private String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
