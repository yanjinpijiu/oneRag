package com.onerag.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.onerag.chat.dao.entity.ConversationDO;
import com.onerag.chat.dao.entity.ConversationMessageDO;
import com.onerag.chat.dao.mapper.ConversationMapper;
import com.onerag.chat.dao.mapper.ConversationMessageMapper;
import com.onerag.chat.service.ConversationService;
import com.onerag.chat.service.ChatModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对话持久化服务实现。
 * 封装会话与消息的 CRUD，以及历史上下文构建/压缩能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {
    private static final String DEBUG_LOG_PATH = "debug-a4f8e1.log";

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final ChatModelService chatModelService;

    /**
     * 创建新会话并自动写入首条用户消息。
     */
    @Override
    public String createConversation(String userId, String firstMessage) {
        String conversationId = UUID.randomUUID().toString();

        // 创建对话
        Date now = new Date();
        ConversationDO conversation = ConversationDO.builder()
                .conversationId(conversationId)
                .userId(userId)
                .title(firstMessage.length() > 20 ? firstMessage.substring(0, 20) + "..." : firstMessage)
                .lastTime(now)
                .createTime(now)
                .updateTime(now)
                .build();
        conversationMapper.insert(conversation);

        // 添加第一条消息
        addMessage(conversationId, userId, "user", firstMessage);

        log.info("创建新对话: conversationId={}, userId={}, firstMessage={}", conversationId, userId, firstMessage);
        return conversationId;
    }

    /**
     * 查询用户下的会话列表（按最近活跃时间倒序）。
     */
    @Override
    public List<ConversationDO> getConversations(String userId) {
        LambdaQueryWrapper<ConversationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationDO::getUserId, userId)
                .orderByDesc(ConversationDO::getLastTime);
        return conversationMapper.selectList(wrapper);
    }

    @Override
    public ConversationDO getConversation(String conversationId) {
        LambdaQueryWrapper<ConversationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationDO::getConversationId, conversationId);
        return conversationMapper.selectOne(wrapper);
    }

    /**
     * 查询会话消息（按时间正序）。
     */
    @Override
    public List<ConversationMessageDO> getConversationMessages(String conversationId) {
        LambdaQueryWrapper<ConversationMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMessageDO::getConversationId, conversationId)
                .orderByAsc(ConversationMessageDO::getCreateTime);
        return conversationMessageMapper.selectList(wrapper);
    }

    @Override
    public void checkConversationOwner(String conversationId, String userId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId不能为空");
        }
        ConversationDO conversation = getConversation(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权限访问该会话");
        }
    }

    /**
     * 追加一条消息并刷新会话最近活跃时间。
     */
    @Override
    public void addMessage(String conversationId, String userId, String role, String content) {
        // 添加消息
        Date now = new Date();
        ConversationMessageDO message = ConversationMessageDO.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(role)
                .content(content)
                .createTime(now)
                .updateTime(now)
                .build();
        conversationMessageMapper.insert(message);

        // 更新对话最后时间
        ConversationDO conversation = ConversationDO.builder()
                .conversationId(conversationId)
                .lastTime(new Date())
                .build();
        LambdaQueryWrapper<ConversationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationDO::getConversationId, conversationId);
        conversationMapper.update(conversation, wrapper);

        log.info("添加消息: conversationId={}, role={}, content={}", conversationId, role, content);
    }

    /**
     * 将会话消息拼接为模型可消费的对话历史文本。
     */
    @Override
    public String buildContext(String conversationId) {
        List<ConversationMessageDO> messages = getConversationMessages(conversationId);
        StringBuilder context = new StringBuilder();

        for (ConversationMessageDO message : messages) {
            context.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
        }
        // #region agent log
        debugLog("conversation:buildContext", "H13", Map.of(
                "conversationId", conversationId == null ? "null" : conversationId,
                "messageCount", messages.size(),
                "contextLen", context.length(),
                "firstRole", messages.isEmpty() ? "" : String.valueOf(messages.get(0).getRole()),
                "firstLen", messages.isEmpty() || messages.get(0).getContent() == null ? 0 : messages.get(0).getContent().length()
        ));
        // #endregion

        return context.toString();
    }

    /**
     * 调用模型对长上下文做摘要压缩，降低提示词长度。
     */
    @Override
    public String compressContext(String context) {
        // 使用大模型压缩上下文
        try {
            // #region agent log
            debugLog("conversation:compressContext:start", "H14", Map.of(
                    "inputLen", context == null ? 0 : context.length()
            ));
            // #endregion
            String prompt = "请将以下对话历史压缩为简洁的摘要，保留关键信息：\n\n" + context;
            var response = chatModelService.chat(prompt, null);
            log.info("上下文压缩完成，原始长度: {}, 压缩后长度: {}", context.length(), response.getMessage().length());
            // #region agent log
            debugLog("conversation:compressContext:done", "H14", Map.of(
                    "outputLen", response.getMessage() == null ? 0 : response.getMessage().length(),
                    "outputPreview", response.getMessage() == null ? "" : response.getMessage().substring(0, Math.min(response.getMessage().length(), 80))
            ));
            // #endregion
            return response.getMessage();
        } catch (Exception e) {
            log.error("上下文压缩失败，使用原始上下文", e);
            return context;
        }
    }

    /**
     * 更新会话标题（一般在首轮问答后执行）。
     */
    @Override
    public void updateConversationTitle(String conversationId, String title) {
        ConversationDO conversation = ConversationDO.builder()
                .title(title)
                .build();
        LambdaQueryWrapper<ConversationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationDO::getConversationId, conversationId);
        conversationMapper.update(conversation, wrapper);

        log.info("更新对话标题: conversationId={}, title={}", conversationId, title);
    }

    /**
     * 删除会话及其所有消息（逻辑删除）。
     */
    @Override
    public void deleteConversation(String conversationId) {
        // 逻辑删除对话
        LambdaQueryWrapper<ConversationDO> conversationWrapper = new LambdaQueryWrapper<>();
        conversationWrapper.eq(ConversationDO::getConversationId, conversationId);
        conversationMapper.delete(conversationWrapper);

        // 逻辑删除消息
        LambdaQueryWrapper<ConversationMessageDO> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(ConversationMessageDO::getConversationId, conversationId);
        conversationMessageMapper.delete(messageWrapper);

        log.info("删除对话: conversationId={}", conversationId);
    }

    /**
     * 统一 debug 文件打点，便于阶段性问题回放。
     */
    private void debugLog(String message, String hypothesisId, Map<String, Object> data) {
        try {
            String payload = "{\"sessionId\":\"a4f8e1\",\"runId\":\"pre-fix\",\"hypothesisId\":\"" + hypothesisId +
                    "\",\"location\":\"ConversationServiceImpl.java\",\"message\":\"" + message.replace("\"", "\\\"") +
                    "\",\"data\":" + new com.google.gson.Gson().toJson(data) +
                    ",\"timestamp\":" + System.currentTimeMillis() + "}";
            Files.writeString(Path.of(DEBUG_LOG_PATH), payload + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
