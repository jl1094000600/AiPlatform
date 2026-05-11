package com.aipal.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelTrainingDataset {
    private Long id;
    private String name;
    private String path;
    private String source;
    private long records;
    private long sizeBytes;
    private LocalDateTime modifiedTime;
}
