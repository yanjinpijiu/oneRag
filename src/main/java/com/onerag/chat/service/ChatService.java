package com.onerag.chat.service;

import com.onerag.chat.DTO.resp.ChatRespDTO;

/**
 * 对话应用服务。
 * 对外提供同步问答入口，并屏蔽底层编排、检索和模型调用细节。
 */
public interface ChatService {
    /**
     * 发送一条用户消息（可携带既有对话ID）。
     *
     * @param message        用户输入
     * @param conversationId 对话ID；为空时由系统创建新对话
     * @return 模型回复与对话标识
     */
    ChatRespDTO sendAChat(String message, String conversationId);

    /**
     * 发送一条用户消息（新对话场景）。
     *
     * @param message 用户输入
     * @return 模型回复与对话标识
     */
    ChatRespDTO sendAChat(String message);
}
