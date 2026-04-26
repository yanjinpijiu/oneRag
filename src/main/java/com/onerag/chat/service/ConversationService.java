package com.onerag.chat.service;

import com.onerag.chat.dao.entity.ConversationDO;
import com.onerag.chat.dao.entity.ConversationMessageDO;

import java.util.List;

/**
 * 对话会话服务抽象。
 * 管理会话元数据、消息持久化与上下文构建。
 */
public interface ConversationService {

    /**
     * 创建新对话
     * @param userId 用户ID
     * @param firstMessage 第一条消息
     * @return 对话ID
     */
    String createConversation(String userId, String firstMessage);

    /**
     * 获取对话列表
     * @param userId 用户ID
     * @return 对话列表
     */
    List<ConversationDO> getConversations(String userId);

    /**
     * 根据业务会话ID查询会话元信息。
     * @param conversationId 对话ID
     * @return 会话；不存在时返回 null
     */
    ConversationDO getConversation(String conversationId);

    /**
     * 获取对话消息
     * @param conversationId 对话ID
     * @return 消息列表
     */
    List<ConversationMessageDO> getConversationMessages(String conversationId);

    /**
     * 校验会话是否归属指定用户。
     * @param conversationId 对话ID
     * @param userId 用户ID
     */
    void checkConversationOwner(String conversationId, String userId);

    /**
     * 添加消息到对话
     * @param conversationId 对话ID
     * @param userId 用户ID
     * @param role 角色
     * @param content 内容
     */
    void addMessage(String conversationId, String userId, String role, String content);

    /**
     * 构建对话上下文
     * @param conversationId 对话ID
     * @return 上下文字符串
     */
    String buildContext(String conversationId);

    /**
     * 压缩上下文
     * @param context 原始上下文
     * @return 压缩后的上下文
     */
    String compressContext(String context);

    /**
     * 更新对话标题
     * @param conversationId 对话ID
     * @param title 新标题
     */
    void updateConversationTitle(String conversationId, String title);

    /**
     * 删除对话
     * @param conversationId 对话ID
     */
    void deleteConversation(String conversationId);
}
