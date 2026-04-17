package com.aipal.agent.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageRecognitionResponse {
    private String taskId;
    private Integer status;
    private String message;
    private Object result;
    private String fileType;
    private Long processTime;
}
