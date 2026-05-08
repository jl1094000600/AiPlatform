package com.aipal.dto;

import com.aipal.entity.RagIngestionRecord;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RagIngestionResponse {
    private RagIngestionRecord record;
    private String message;
}
