package com.aipal.dto;

import lombok.Data;

@Data
public class RagIngestionRequest {
    private String collectionName;
    private String documentTitle;
    private String content;
    private Long embeddingModelId;
    private String chunkMode;
    private String contentType;
    private Long semanticModelId;
    private String chromaUrl;
    private Integer chunkSize;
    private Integer chunkOverlap;
}
