package com.aipal.agent.image.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class FileParseRequest {
    @NotBlank(message = "文件数据不能为空")
    private String fileData;

    private String fileType;

    private String fileName;

    private String sessionId;

    private String sourceAgent;
}
