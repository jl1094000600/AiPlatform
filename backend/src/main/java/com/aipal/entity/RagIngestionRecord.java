package com.aipal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rag_ingestion_record")
public class RagIngestionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String collectionName;
    private String documentTitle;
    private Long embeddingModelId;
    private String embeddingModelCode;
    private String chunkMode;
    private String contentType;
    private Long semanticModelId;
    private String semanticModelCode;
    private String chromaUrl;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer chunkCount;
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
