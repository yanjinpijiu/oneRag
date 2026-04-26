package com.onerag.chat.service;

import com.onerag.chat.DTO.resp.ChatRespDTO;

/**
 * 大模型适配层抽象。
 * 负责定义同步调用与流式调用的统一接口，便于后续切换不同模型供应商。
 */
public interface ChatModelService {
    /**
     * 同步调用模型并返回完整答案。
     *
     * @param message 用户问题
     * @param context 组装后的上下文（对话历史 + RAG 片段）
     * @return 模型回复
     */
    ChatRespDTO chat(String message, String context);

    /**
     * 兼容旧签名：默认按非深度思考模式流式返回。
     */
    default void streamChat(String message, String context, StreamCallback callback) {
        streamChat(message, context, false, callback);
    }

    /**
     * 流式调用模型。
     *
     * @param message      用户问题
     * @param context      组装后的上下文
     * @param deepThinking 是否透出推理过程
     * @param callback     流式回调
     */
    void streamChat(String message, String context, boolean deepThinking, StreamCallback callback);
}
