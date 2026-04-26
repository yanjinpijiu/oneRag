package com.onerag.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用响应对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> CommonResponse<T> ok(T data) {
        return new CommonResponse<>("0", "ok", data);
    }

    public static <T> CommonResponse<T> fail(String code, String message) {
        return new CommonResponse<>(code, message, null);
    }
}
