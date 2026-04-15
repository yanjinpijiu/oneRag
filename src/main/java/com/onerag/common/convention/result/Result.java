package com.onerag.common.convention.result;

import lombok.Data;
import org.apache.poi.ss.formula.functions.T;

/**
 * 全局返回对象
 */
@Data
public class Result {
    /**
     * 正确返回码
     */
    public static final String SUCCESS_CODE = "0";

    /**
     * 返回码
     */
    private String code;

    /**
     * 返回消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 是否为成功响应。
     */
    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
