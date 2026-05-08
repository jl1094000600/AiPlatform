package com.aipal.service;

import com.aipal.dto.RagIngestionRequest;
import com.aipal.dto.RagIngestionResponse;
import com.aipal.entity.AiModel;
import com.aipal.entity.RagIngestionRecord;
import com.aipal.mapper.AiModelMapper;
import com.aipal.mapper.RagIngestionRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RagService {
    private static final String DEFAULT_CHROMA_URL = "http://localhost:9000";
    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;
    private static final int MAX_CONTENT_LENGTH = 200_000;

    private final RagIngestionRecordMapper recordMapper;
    private final AiModelMapper modelMapper;
    private final RagChunker chunker;
    private final ObjectMapper objectMapper;

    public RagIngestionResponse ingest(RagIngestionRequest request) {
        validate(request);
        AiModel model = modelMapper.selectById(request.getEmbeddingModelId());
        if (model == null || model.getStatus() == null || model.getStatus() != 1) {
            throw new IllegalArgumentException("Embedding model is not available");
        }

        int chunkSize = request.getChunkSize() == null ? DEFAULT_CHUNK_SIZE : request.getChunkSize();
        int chunkOverlap = request.getChunkOverlap() == null ? DEFAULT_CHUNK_OVERLAP : request.getChunkOverlap();
        List<String> chunks = chunker.chunk(request.getContent(), chunkSize, chunkOverlap);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document content is required");
        }

        RagIngestionRecord record = createRecord(request, model, chunkSize, chunkOverlap, chunks.size());
        recordMapper.insert(record);

        try {
            List<List<Double>> embeddings = createEmbeddings(model, chunks);
            writeToChroma(record, chunks, embeddings);
            record.setStatus("SUCCESS");
            record.setErrorMessage(null);
            record.setUpdateTime(LocalDateTime.now());
            recordMapper.updateById(record);
            return new RagIngestionResponse(record, "Document chunks were embedded and stored in Chroma");
        } catch (Exception ex) {
            record.setStatus("FAILED");
            record.setErrorMessage(limitMessage(ex.getMessage()));
            record.setUpdateTime(LocalDateTime.now());
            recordMapper.updateById(record);
            throw new IllegalStateException("RAG ingestion failed: " + ex.getMessage(), ex);
        }
    }

    public Page<RagIngestionRecord> list(int pageNum, int pageSize) {
        Page<RagIngestionRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RagIngestionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(RagIngestionRecord::getCreateTime);
        return recordMapper.selectPage(page, wrapper);
    }

    private void validate(RagIngestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (isBlank(request.getCollectionName())) {
            throw new IllegalArgumentException("Collection name is required");
        }
        if (!request.getCollectionName().matches("[A-Za-z0-9._-]{3,63}")) {
            throw new IllegalArgumentException("Collection name must be 3-63 characters and only contain letters, numbers, '.', '_' or '-'");
        }
        if (request.getEmbeddingModelId() == null) {
            throw new IllegalArgumentException("Embedding model is required");
        }
        if (isBlank(request.getContent())) {
            throw new IllegalArgumentException("Document content is required");
        }
        if (request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Document content is too large");
        }
        int chunkSize = request.getChunkSize() == null ? DEFAULT_CHUNK_SIZE : request.getChunkSize();
        int chunkOverlap = request.getChunkOverlap() == null ? DEFAULT_CHUNK_OVERLAP : request.getChunkOverlap();
        if (chunkSize < 100 || chunkSize > 4000) {
            throw new IllegalArgumentException("Chunk size must be between 100 and 4000");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("Chunk overlap must be >= 0 and less than chunk size");
        }
    }

    private RagIngestionRecord createRecord(RagIngestionRequest request, AiModel model, int chunkSize, int chunkOverlap, int chunkCount) {
        RagIngestionRecord record = new RagIngestionRecord();
        record.setCollectionName(request.getCollectionName().trim());
        record.setDocumentTitle(isBlank(request.getDocumentTitle()) ? "Untitled Document" : request.getDocumentTitle().trim());
        record.setEmbeddingModelId(model.getId());
        record.setEmbeddingModelCode(model.getModelCode());
        record.setChromaUrl(normalizeBaseUrl(request.getChromaUrl()));
        record.setChunkSize(chunkSize);
        record.setChunkOverlap(chunkOverlap);
        record.setChunkCount(chunkCount);
        record.setStatus("RUNNING");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private List<List<Double>> createEmbeddings(AiModel model, List<String> chunks) throws Exception {
        if (isBlank(model.getEndpoint())) {
            throw new IllegalArgumentException("Embedding model endpoint is required");
        }
        String endpoint = embeddingsEndpoint(model.getEndpoint());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model.getModelCode());
        payload.put("input", chunks);

        RestClient.RequestBodySpec request = RestClient.create()
                .post()
                .uri(endpoint)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (!isBlank(model.getApiKey()) && !"******".equals(model.getApiKey())) {
            request.header("Authorization", "Bearer " + model.getApiKey());
        }

        String body = request.body(payload).retrieve().body(String.class);
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() != chunks.size()) {
            throw new IllegalStateException("Embedding response size does not match chunk count");
        }

        List<List<Double>> embeddings = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode vector = item.path("embedding");
            if (!vector.isArray() || vector.isEmpty()) {
                throw new IllegalStateException("Embedding response contains an empty vector");
            }
            List<Double> values = new ArrayList<>();
            for (JsonNode number : vector) {
                values.add(number.asDouble());
            }
            embeddings.add(values);
        }
        return embeddings;
    }

    private void writeToChroma(RagIngestionRecord record, List<String> chunks, List<List<Double>> embeddings) throws Exception {
        String baseUrl = normalizeBaseUrl(record.getChromaUrl());
        RestClient client = RestClient.create(baseUrl);

        Map<String, Object> payload = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ids.add("rag-" + record.getId() + "-" + i);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("recordId", record.getId());
            metadata.put("documentTitle", record.getDocumentTitle());
            metadata.put("chunkIndex", i);
            metadata.put("source", "AIPlatform");
            metadatas.add(metadata);
        }
        payload.put("ids", ids);
        payload.put("embeddings", embeddings);
        payload.put("documents", chunks);
        payload.put("metadatas", metadatas);

        try {
            writeToChromaV2(client, record.getCollectionName(), payload);
            return;
        } catch (RestClientException ex) {
            // Older local deployments may still expose v1; keep a fallback for compatibility.
        }
        writeToChromaV1(client, record.getCollectionName(), payload);
    }

    private void writeToChromaV2(RestClient client, String collectionName, Map<String, Object> payload) throws Exception {
        String collectionId = ensureCollectionV2(client, collectionName);
        String path = "/api/v2/tenants/default_tenant/databases/default_database/collections/" + collectionId + "/add";
        client.post().uri(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(payload).retrieve().toBodilessEntity();
    }

    private void writeToChromaV1(RestClient client, String collectionName, Map<String, Object> payload) throws Exception {
        String collectionId = ensureCollectionV1(client, collectionName);
        RestClientException lastError = null;
        for (String path : List.of("/api/v1/collections/" + collectionId + "/add",
                "/api/v1/collections/" + collectionName + "/add")) {
            try {
                client.post().uri(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(payload).retrieve().toBodilessEntity();
                return;
            } catch (RestClientException ex) {
                lastError = ex;
            }
        }
        throw lastError == null ? new IllegalStateException("Unable to add chunks to Chroma") : lastError;
    }

    private String ensureCollectionV2(RestClient client, String collectionName) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", collectionName);
        payload.put("get_or_create", true);
        payload.put("metadata", Map.of("source", "AIPlatform"));

        String path = "/api/v2/tenants/default_tenant/databases/default_database/collections";
        String body = client.post().uri(path)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        String id = extractCollectionId(body, collectionName);
        if (!isBlank(id)) {
            return id;
        }

        body = client.get().uri(path + "?limit=100&offset=0").retrieve().body(String.class);
        id = extractCollectionId(body, collectionName);
        if (!isBlank(id)) {
            return id;
        }
        throw new IllegalStateException("Unable to resolve Chroma v2 collection id");
    }

    private String ensureCollectionV1(RestClient client, String collectionName) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", collectionName);
        payload.put("metadata", Map.of("source", "AIPlatform"));
        try {
            String body = client.post().uri("/api/v1/collections")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            String id = extractCollectionId(body, collectionName);
            if (!isBlank(id)) {
                return id;
            }
        } catch (RestClientException ignored) {
            // Existing collections and Chroma version differences are handled by lookup below.
        }

        try {
            String body = client.get().uri("/api/v1/collections").retrieve().body(String.class);
            String id = extractCollectionId(body, collectionName);
            if (!isBlank(id)) {
                return id;
            }
        } catch (RestClientException ignored) {
            // Fall through to name-based endpoints.
        }
        return collectionName;
    }

    private String extractCollectionId(String body, String collectionName) throws Exception {
        if (isBlank(body)) {
            return null;
        }
        JsonNode root = objectMapper.readTree(body);
        if (root.isArray()) {
            for (JsonNode item : root) {
                String id = idIfNameMatches(item, collectionName);
                if (!isBlank(id)) {
                    return id;
                }
            }
            return null;
        }
        JsonNode value = root.path("value");
        if (value.isArray()) {
            for (JsonNode item : value) {
                String id = idIfNameMatches(item, collectionName);
                if (!isBlank(id)) {
                    return id;
                }
            }
        }
        return idIfNameMatches(root, collectionName);
    }

    private String idIfNameMatches(JsonNode node, String collectionName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String name = node.path("name").asText("");
        if (!collectionName.equals(name)) {
            return null;
        }
        String id = node.path("id").asText("");
        return isBlank(id) ? collectionName : id;
    }

    private String embeddingsEndpoint(String endpoint) {
        String normalized = normalizeBaseUrl(endpoint);
        return normalized.endsWith("/embeddings") ? normalized : normalized + "/embeddings";
    }

    private String normalizeBaseUrl(String value) {
        String url = isBlank(value) ? DEFAULT_CHROMA_URL : value.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String limitMessage(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
