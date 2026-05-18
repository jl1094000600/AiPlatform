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
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
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
    private final RagHybridChunker hybridChunker;
    private final ObjectMapper objectMapper;

    public RagIngestionResponse ingest(RagIngestionRequest request) {
        validate(request);
        AiModel model = modelMapper.selectById(request.getEmbeddingModelId());
        if (model == null || model.getStatus() == null || model.getStatus() != 1) {
            throw new IllegalArgumentException("Embedding model is not available");
        }

        int chunkSize = request.getChunkSize() == null ? DEFAULT_CHUNK_SIZE : request.getChunkSize();
        int chunkOverlap = request.getChunkOverlap() == null ? DEFAULT_CHUNK_OVERLAP : request.getChunkOverlap();
        String chunkMode = normalizeChunkMode(request.getChunkMode());
        AiModel semanticModel = resolveSemanticModel(request, chunkMode);
        ChunkPlan chunkPlan = createChunks(request, chunkSize, chunkOverlap, chunkMode, semanticModel);
        List<String> chunks = chunkPlan.chunks();
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document content is required");
        }

        RagIngestionRecord record = createRecord(request, model, semanticModel, chunkPlan, chunkSize, chunkOverlap, chunks.size());
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

    public List<Map<String, Object>> listChromaCollections(String chromaUrl) {
        RestClient client = createChromaV2Client(chromaUrl);
        try {
            String body = client.get().uri(chromaCollectionsPath() + "?limit=100&offset=0").retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            List<Map<String, Object>> collections = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    Map<String, Object> collection = new LinkedHashMap<>();
                    String id = item.path("id").asText("");
                    collection.put("id", id);
                    collection.put("name", item.path("name").asText(""));
                    collection.put("dimension", item.path("dimension").isNumber() ? item.path("dimension").asInt() : null);
                    collection.put("metadata", readObject(item.path("metadata")));
                    collection.put("count", isBlank(id) ? 0 : countChromaDocuments(client, id));
                    collections.add(collection);
                }
            }
            return collections;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list Chroma collections: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> listChromaDocuments(String chromaUrl, String collectionId, int limit, int offset) {
        if (isBlank(collectionId) || collectionId.contains("/") || collectionId.contains("\\")) {
            throw new IllegalArgumentException("collectionId is required");
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        RestClient client = createChromaV2Client(chromaUrl);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("include", List.of("documents", "metadatas"));
            payload.put("limit", safeLimit);
            payload.put("offset", safeOffset);

            String body = client.post().uri(chromaCollectionsPath() + "/" + collectionId + "/get")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            List<Map<String, Object>> documents = new ArrayList<>();
            JsonNode ids = root.path("ids");
            JsonNode docs = root.path("documents");
            JsonNode metadatas = root.path("metadatas");
            for (int i = 0; ids.isArray() && i < ids.size(); i++) {
                JsonNode metadata = metadatas.isArray() && i < metadatas.size() ? metadatas.get(i) : objectMapper.createObjectNode();
                String document = docs.isArray() && i < docs.size() ? docs.get(i).asText("") : "";
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", ids.get(i).asText(""));
                row.put("document", document);
                row.put("preview", document.length() > 260 ? document.substring(0, 260) + "..." : document);
                row.put("metadata", readObject(metadata));
                row.put("documentTitle", metadata.path("documentTitle").asText(""));
                row.put("recordId", metadata.path("recordId").isMissingNode() ? null : metadata.path("recordId").asText(""));
                row.put("chunkIndex", metadata.path("chunkIndex").isMissingNode() ? null : metadata.path("chunkIndex").asText(""));
                documents.add(row);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("collectionId", collectionId);
            result.put("limit", safeLimit);
            result.put("offset", safeOffset);
            result.put("total", countChromaDocuments(client, collectionId));
            result.put("documents", documents);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list Chroma documents: " + e.getMessage(), e);
        }
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
        normalizeChunkMode(request.getChunkMode());
    }

    private ChunkPlan createChunks(RagIngestionRequest request, int chunkSize, int chunkOverlap,
                                   String chunkMode, AiModel semanticModel) {
        if ("HYBRID".equals(chunkMode)) {
            RagHybridChunker.HybridChunkResult result = hybridChunker.chunk(
                    request.getContent(),
                    request.getContentType(),
                    chunkSize,
                    chunkOverlap,
                    semanticModel
            );
            return new ChunkPlan(result.chunks(), chunkMode, result.contentType(), result.semanticUsed());
        }
        List<String> chunks = chunker.chunk(request.getContent(), chunkSize, chunkOverlap);
        String contentType = hybridChunker.normalizeContentType(request.getContentType(), request.getContent());
        return new ChunkPlan(chunks, chunkMode, contentType, false);
    }

    private AiModel resolveSemanticModel(RagIngestionRequest request, String chunkMode) {
        if (!"HYBRID".equals(chunkMode) || request.getSemanticModelId() == null) {
            return null;
        }
        AiModel semanticModel = modelMapper.selectById(request.getSemanticModelId());
        if (semanticModel == null || semanticModel.getStatus() == null || semanticModel.getStatus() != 1) {
            throw new IllegalArgumentException("Semantic chunking model is not available");
        }
        return semanticModel;
    }

    private String normalizeChunkMode(String value) {
        String mode = isBlank(value) ? "FIXED" : value.trim().toUpperCase();
        if (!"FIXED".equals(mode) && !"HYBRID".equals(mode)) {
            throw new IllegalArgumentException("Chunk mode must be FIXED or HYBRID");
        }
        return mode;
    }

    private RagIngestionRecord createRecord(RagIngestionRequest request, AiModel model, AiModel semanticModel,
                                            ChunkPlan chunkPlan, int chunkSize, int chunkOverlap, int chunkCount) {
        RagIngestionRecord record = new RagIngestionRecord();
        record.setCollectionName(request.getCollectionName().trim());
        record.setDocumentTitle(isBlank(request.getDocumentTitle()) ? "Untitled Document" : request.getDocumentTitle().trim());
        record.setEmbeddingModelId(model.getId());
        record.setEmbeddingModelCode(model.getModelCode());
        record.setChunkMode(chunkPlan.mode());
        record.setContentType(chunkPlan.contentType());
        record.setSemanticModelId(semanticModel == null ? null : semanticModel.getId());
        record.setSemanticModelCode(semanticModel == null ? null : semanticModel.getModelCode());
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
        RestClient client = createChromaV2Client(record.getChromaUrl());

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
            metadata.put("chunkMode", record.getChunkMode());
            metadata.put("contentType", record.getContentType());
            if (!isBlank(record.getSemanticModelCode())) {
                metadata.put("semanticModelCode", record.getSemanticModelCode());
            }
            metadatas.add(metadata);
        }
        payload.put("ids", ids);
        payload.put("embeddings", embeddings);
        payload.put("documents", chunks);
        payload.put("metadatas", metadatas);

        try {
            writeToChromaV2(client, record.getCollectionName(), payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Chroma v2 ingestion failed: " + ex.getMessage(), ex);
        }
    }

    private void writeToChromaV2(RestClient client, String collectionName, Map<String, Object> payload) throws Exception {
        String collectionId = ensureCollectionV2(client, collectionName);
        String path = "/api/v2/tenants/default_tenant/databases/default_database/collections/" + collectionId + "/add";
        client.post().uri(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(payload).retrieve().toBodilessEntity();
    }

    private String ensureCollectionV2(RestClient client, String collectionName) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", collectionName);
        payload.put("get_or_create", true);
        payload.put("metadata", Map.of("source", "AIPlatform"));

        String path = "/api/v2/tenants/default_tenant/databases/default_database/collections";
        try {
            String body = client.post().uri(path)
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

        String body = client.get().uri(path + "?limit=100&offset=0").retrieve().body(String.class);
        String id = extractCollectionId(body, collectionName);
        if (!isBlank(id)) {
            return id;
        }
        throw new IllegalStateException("Unable to resolve Chroma v2 collection id");
    }

    private boolean isChromaV2(RestClient client) {
        try {
            client.get().uri("/api/v2/heartbeat").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            return false;
        }
    }

    private RestClient createChromaV2Client(String chromaUrl) {
        String baseUrl = resolveReachableChromaBaseUrl(chromaUrl);
        return RestClient.create(baseUrl);
    }

    private String resolveReachableChromaBaseUrl(String chromaUrl) {
        List<String> errors = new ArrayList<>();
        for (String candidate : chromaBaseUrlCandidates(normalizeBaseUrl(chromaUrl))) {
            RestClient client = RestClient.create(candidate);
            if (isChromaV2(client)) {
                return candidate;
            }
            errors.add(candidate);
        }
        throw new IllegalStateException("Chroma v2 heartbeat is unavailable. Tried: " + String.join(", ", errors));
    }

    private List<String> chromaBaseUrlCandidates(String baseUrl) {
        List<String> candidates = new ArrayList<>();
        candidates.add(baseUrl);
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            int port = uri.getPort();
            String portPart = port > 0 ? ":" + port : "";
            String host = uri.getHost();
            if ("localhost".equalsIgnoreCase(host)) {
                candidates.add(scheme + "://[::1]" + portPart);
                candidates.add(scheme + "://127.0.0.1" + portPart);
            }
        } catch (IllegalArgumentException ignored) {
            // Keep the original URL as the only candidate.
        }
        return candidates.stream().distinct().toList();
    }

    private String chromaCollectionsPath() {
        return "/api/v2/tenants/default_tenant/databases/default_database/collections";
    }

    private int countChromaDocuments(RestClient client, String collectionId) {
        try {
            String body = client.get().uri(chromaCollectionsPath() + "/" + collectionId + "/count").retrieve().body(String.class);
            return Integer.parseInt(body == null ? "0" : body.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Map<String, Object> readObject(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
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

    private record ChunkPlan(List<String> chunks, String mode, String contentType, boolean semanticUsed) {
    }
}
