package com.onerag.user.controller;

import com.onerag.user.dto.CommonResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 用户模块异常处理。
 */
@RestControllerAdvice(basePackages = "com.onerag.user")
public class UserExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return CommonResponse.fail("400", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResponse<Void> handleValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "参数错误"
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return CommonResponse.fail("400", message);
    }

    @ExceptionHandler(Exception.class)
    public CommonResponse<Void> handleException(Exception ex) {
        return CommonResponse.fail("500", ex.getMessage() == null ? "系统异常" : ex.getMessage());
    }
}
