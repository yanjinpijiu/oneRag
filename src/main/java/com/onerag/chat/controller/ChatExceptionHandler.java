package com.onerag.chat.controller;

import com.onerag.user.dto.CommonResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 聊天模块异常处理。
 */
@RestControllerAdvice(basePackages = "com.onerag.chat")
public class ChatExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return CommonResponse.fail("400", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public CommonResponse<Void> handleException(Exception ex) {
        return CommonResponse.fail("500", ex.getMessage() == null ? "系统异常" : ex.getMessage());
    }
}
