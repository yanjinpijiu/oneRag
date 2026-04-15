package com.onerag.chat.DTO.req;

import lombok.Data;

/**
 * 聊天请求参数。
 */
@Data
public class ChatReqDTO {
    /**
     * 单次聊天内容
     */
    private String message;
    
    /**
     * 对话ID（可选）
     */
    private String conversationId;

    /**
     * 是否开启深度思考（默认 false）
     */
    private Boolean deepThinking;
}
