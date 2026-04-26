package com.onerag.chat.orchestrator;

/**
 * 聊天编排服务。
 * 在模型调用前完成会话上下文准备、改写、意图识别与检索等预处理。
 */
public interface ChatOrchestratorService {

    /**
     * 执行一次完整编排并返回统一编排结果。
     *
     * @param message        用户输入
     * @param conversationId 对话ID（可为空）
     * @return 编排后的上下文与检索结果
     */
    OrchestrationResult orchestrate(String message, String conversationId, String userId);

    default OrchestrationResult orchestrate(String message, String conversationId) {
        return orchestrate(message, conversationId, "default");
    }
}
