package com.onerag.chat.service.impl;

import com.onerag.chat.DTO.resp.ChatRespDTO;
import com.onerag.chat.orchestrator.ChatOrchestratorService;
import com.onerag.chat.orchestrator.OrchestrationResult;
import com.onerag.chat.service.ChatModelService;
import com.onerag.chat.service.ChatService;
import com.onerag.chat.service.ConversationService;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 同步问答服务实现。
 * 负责串联编排层与模型层，并在返回前落库助手消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatModelService chatModelService;
    private final ConversationService conversationService;

    /**
     * 执行一次同步问答流程：
     * 1) 编排上下文 2) 调用模型 3) 记录回复并维护标题。
     */
    @Override
    public ChatRespDTO sendAChat(String message, String conversationId) {
        try {
            String currentUserId = resolveCurrentUserId();
            OrchestrationResult result = chatOrchestratorService.orchestrate(message, conversationId, currentUserId);
            long llmStart = System.currentTimeMillis();
            ChatRespDTO response = chatModelService.chat(message, result.getFullContext());

            // 添加助手回复到对话
            conversationService.addMessage(result.getConversationId(), currentUserId, "assistant", response.getMessage());

            // 更新对话标题（如果是第一条消息）
            if (conversationService.getConversationMessages(result.getConversationId()).size() == 2) {
                conversationService.updateConversationTitle(result.getConversationId(),
                        response.getMessage().length() > 20 ? response.getMessage().substring(0, 20) + "..."
                                : response.getMessage());
            }

            log.info("chat-sync|requestId={}|conversationId={}|orchestrateMs={}|llmMs={}|replyLen={}",
                    result.getRequestId(), result.getConversationId(), result.getTotalCostMs(),
                    System.currentTimeMillis() - llmStart, response.getMessage().length());
            response.setConversationId(result.getConversationId());
            return response;

        } catch (Exception e) {
            log.error("处理聊天请求失败", e);
            return new ChatRespDTO("抱歉，处理您的请求时出现错误：" + e.getMessage(), conversationId);
        }
    }

    /**
     * 新会话快捷入口。
     */
    @Override
    public ChatRespDTO sendAChat(String message) {
        return sendAChat(message, null);
    }

    private String resolveCurrentUserId() {
        try {
            StpUtil.checkLogin();
            return String.valueOf(StpUtil.getLoginId());
        } catch (Exception e) {
            return "default";
        }
    }
}
