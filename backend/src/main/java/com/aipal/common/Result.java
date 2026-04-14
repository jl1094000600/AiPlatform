package com.aipal.common;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> unauthorized(String message) {
        Result<T> result = new Result<>();
        result.setCode(401);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> forbidden(String message) {
        Result<T> result = new Result<>();
        result.setCode(403);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> badRequest(String message) {
        Result<T> result = new Result<>();
        result.setCode(400);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> serverError(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
}
