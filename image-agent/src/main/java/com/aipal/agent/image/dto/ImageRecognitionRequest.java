package com.aipal.agent.image.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImageRecognitionRequest {
    @NotBlank(message = "输入类型不能为空")
    private String inputType;
    
    @NotBlank(message = "输入数据不能为空")
    private String inputData;
    
    private String sessionId;
    private String traceId;
}
