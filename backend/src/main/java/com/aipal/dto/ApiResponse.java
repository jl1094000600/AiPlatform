package com.aipal.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private Integer code;
    private String message;
    private T data;
    private String timestamp;
    private String traceId;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        response.setTimestamp(java.time.LocalDateTime.now().toString());
        return response;
    }

    public static <T> ApiResponse<T> error(Integer code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setTimestamp(java.time.LocalDateTime.now().toString());
        return response;
    }
}
