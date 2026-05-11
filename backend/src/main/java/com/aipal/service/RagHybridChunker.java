package com.aipal.service;

import com.aipal.entity.AiModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RagHybridChunker {
    private static final int MAX_CANDIDATES_FOR_MODEL = 80;
    private static final int MAX_MODEL_TEXT_CHARS = 24_000;
    private static final Pattern CODE_LINE_PATTERN = Pattern.compile(
            "^\\s*(package\\s+|import\\s+|public\\s+|private\\s+|protected\\s+|class\\s+|interface\\s+|enum\\s+|@\\w+|def\\s+|function\\s+|const\\s+|let\\s+|var\\s+|if\\s*\\(|for\\s*\\(|while\\s*\\(|try\\s*\\{|catch\\s*\\()"
    );

    private final RagChunker fixedChunker;
    private final ObjectMapper objectMapper;

    public HybridChunkResult chunk(String content, String requestedContentType, int chunkSize, int overlap, AiModel semanticModel) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return new HybridChunkResult(List.of(), normalizeContentType(requestedContentType, normalized), false);
        }

        String effectiveContentType = normalizeContentType(requestedContentType, normalized);
        List<String> candidates = splitByStructure(normalized, effectiveContentType);
        if (candidates.isEmpty()) {
            candidates = fixedChunker.chunk(normalized, chunkSize, overlap);
        }

        List<String> semanticChunks = requestSemanticChunks(candidates, normalized, effectiveContentType, chunkSize, semanticModel);
        List<String> source = semanticChunks.isEmpty() ? candidates : semanticChunks;
        List<String> finalChunks = enforceChunkSize(source, chunkSize, overlap);
        if (finalChunks.isEmpty()) {
            finalChunks = fixedChunker.chunk(normalized, chunkSize, overlap);
        }
        return new HybridChunkResult(finalChunks, effectiveContentType, !semanticChunks.isEmpty());
    }

    public String normalizeContentType(String contentType, String content) {
        String value = contentType == null || contentType.isBlank() ? "AUTO" : contentType.trim().toUpperCase();
        if ("CODE".equals(value) || "DOCUMENT".equals(value)) {
            return value;
        }
        return looksLikeCode(content) ? "CODE" : "DOCUMENT";
    }

    private List<String> splitByStructure(String content, String contentType) {
        return "CODE".equals(contentType) ? splitCode(content) : splitDocument(content);
    }

    private List<String> splitDocument(String content) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] lines = content.split("\\R", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            boolean heading = trimmed.matches("^#{1,6}\\s+.+") || trimmed.matches("^\\d+(\\.\\d+)*\\s+.+");
            boolean blank = trimmed.isEmpty();
            if (heading && !current.isEmpty()) {
                flush(segments, current);
            }
            if (blank) {
                flush(segments, current);
                continue;
            }
            current.append(line).append('\n');
        }
        flush(segments, current);
        return segments;
    }

    private List<String> splitCode(String content) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String[] lines = content.split("\\R", -1);
        boolean inFence = false;
        for (String line : lines) {
            String trimmed = line.trim();
            boolean fence = trimmed.startsWith("```");
            if (fence) {
                inFence = !inFence;
            }
            boolean boundary = !inFence && (trimmed.isEmpty()
                    || trimmed.matches(".*\\b(class|interface|enum)\\s+\\w+.*")
                    || trimmed.matches("\\s*(public|private|protected)?\\s*(static\\s+)?[\\w<>\\[\\], ?]+\\s+\\w+\\s*\\([^;]*\\)\\s*\\{?\\s*")
                    || trimmed.matches("\\s*(def|function)\\s+\\w+\\s*\\(.*"));
            if (boundary && !current.isEmpty()) {
                flush(segments, current);
            }
            if (!trimmed.isEmpty()) {
                current.append(line).append('\n');
            }
        }
        flush(segments, current);
        return segments;
    }

    private void flush(List<String> segments, StringBuilder current) {
        String value = current.toString().trim();
        if (!value.isBlank()) {
            segments.add(value);
        }
        current.setLength(0);
    }

    private List<String> requestSemanticChunks(List<String> candidates, String original, String contentType,
                                               int chunkSize, AiModel semanticModel) {
        if (semanticModel == null || isBlank(semanticModel.getEndpoint())) {
            return List.of();
        }
        try {
            String endpoint = semanticModel.getEndpoint().endsWith("/")
                    ? semanticModel.getEndpoint() + "chat/completions"
                    : semanticModel.getEndpoint() + "/chat/completions";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", semanticModel.getModelCode());
            body.put("temperature", semanticModel.getDefaultTemperature() == null ? BigDecimal.valueOf(0.1) : semanticModel.getDefaultTemperature());
            body.put("max_tokens", Math.min(semanticModel.getMaxTokens() == null ? 4096 : semanticModel.getMaxTokens(), 8192));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You split RAG source text into semantically coherent chunks. Return strict JSON only."),
                    Map.of("role", "user", "content", buildPrompt(candidates, contentType, chunkSize))
            ));

            RestClient.RequestBodySpec request = RestClient.create()
                    .post()
                    .uri(endpoint)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON);
            if (!isBlank(semanticModel.getApiKey()) && !"******".equals(semanticModel.getApiKey())) {
                request.header("Authorization", "Bearer " + semanticModel.getApiKey());
            }
            String response = request.body(body).retrieve().body(String.class);
            String content = extractModelContent(response);
            return validateSemanticChunks(parseChunks(content), original, chunkSize);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String buildPrompt(List<String> candidates, String contentType, int chunkSize) throws Exception {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(candidates.size(), MAX_CANDIDATES_FOR_MODEL);
        int used = 0;
        for (int i = 0; i < limit; i++) {
            String item = candidates.get(i);
            if (used + item.length() > MAX_MODEL_TEXT_CHARS) {
                break;
            }
            used += item.length();
            builder.append("[").append(i).append("]\n").append(item).append("\n\n");
        }
        return """
                Content type: %s
                Target max chunk size: %d characters.
                Merge or split these ordered candidate segments into semantically complete RAG chunks.
                Preserve the source language and original wording as much as possible.
                Return JSON only in this exact shape:
                {"chunks":["chunk text 1","chunk text 2"]}

                Candidate segments:
                %s
                """.formatted(contentType, chunkSize, builder);
    }

    private String extractModelContent(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return content.trim();
    }

    private List<String> parseChunks(String content) throws Exception {
        if (isBlank(content)) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(content);
        JsonNode chunksNode = root.isArray() ? root : root.path("chunks");
        if (!chunksNode.isArray()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        for (JsonNode item : chunksNode) {
            String value = item.isTextual() ? item.asText("") : item.path("text").asText("");
            if (!isBlank(value)) {
                chunks.add(value.trim());
            }
        }
        return chunks;
    }

    private List<String> validateSemanticChunks(List<String> chunks, String original, int chunkSize) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        int total = chunks.stream().mapToInt(String::length).sum();
        if (total < Math.max(1, original.trim().length() / 2)) {
            return List.of();
        }
        boolean tooLong = chunks.stream().anyMatch(chunk -> chunk.length() > chunkSize * 3);
        return tooLong ? List.of() : chunks;
    }

    private List<String> enforceChunkSize(List<String> source, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : source) {
            String value = part == null ? "" : part.trim();
            if (value.isBlank()) {
                continue;
            }
            if (value.length() > chunkSize) {
                flushSized(result, current, chunkSize, overlap);
                result.addAll(fixedChunker.chunk(value, chunkSize, overlap));
                continue;
            }
            int nextSize = current.isEmpty() ? value.length() : current.length() + 2 + value.length();
            if (nextSize > chunkSize) {
                flushSized(result, current, chunkSize, overlap);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(value);
        }
        flushSized(result, current, chunkSize, overlap);
        return result;
    }

    private void flushSized(List<String> result, StringBuilder current, int chunkSize, int overlap) {
        String value = current.toString().trim();
        if (!value.isBlank()) {
            result.addAll(fixedChunker.chunk(value, chunkSize, overlap));
        }
        current.setLength(0);
    }

    private boolean looksLikeCode(String content) {
        if (isBlank(content)) {
            return false;
        }
        String[] lines = content.split("\\R");
        int checked = 0;
        int hits = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            checked++;
            if (CODE_LINE_PATTERN.matcher(trimmed).find()
                    || trimmed.endsWith(";")
                    || trimmed.endsWith("{")
                    || trimmed.endsWith("}")) {
                hits++;
            }
            if (checked >= 80) {
                break;
            }
        }
        return hits >= 4 || (checked > 0 && hits * 100 / checked >= 30);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record HybridChunkResult(List<String> chunks, String contentType, boolean semanticUsed) {
    }
}
