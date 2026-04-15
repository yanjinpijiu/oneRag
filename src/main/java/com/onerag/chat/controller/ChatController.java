package com.onerag.chat.controller;

import com.onerag.chat.DTO.req.ChatReqDTO;
import com.onerag.chat.DTO.resp.ChatRespDTO;
import com.onerag.chat.config.ChatFlowProperties;
import com.onerag.chat.orchestrator.ChatOrchestratorService;
import com.onerag.chat.orchestrator.OrchestrationResult;
import com.onerag.chat.service.ChatModelService;
import com.onerag.chat.service.ChatService;
import com.onerag.chat.service.ConversationService;
import com.onerag.chat.service.StreamCallback;
import com.onerag.chat.util.SiliconFlowAssistantText;
import com.onerag.document.dto.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天接口控制器。
 * 提供同步与流式 SSE 两类接口，并负责把编排/模型事件转成前端可消费事件流。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private static final String DEBUG_LOG_PATH = "debug-a4f8e1.log";

    private final ChatService chatService;
    private final ChatModelService chatModelService;
    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatFlowProperties chatFlowProperties;
    private final ConversationService conversationService;
    private final ExecutorService chatStreamExecutor;
    @Value("${ai.chat.default-model}")
    private String defaultChatModel;

    /**
     * 同步问答接口。
     */
    @PostMapping("/send")
    public ResponseEntity<ChatRespDTO> sendAChat(@RequestBody ChatReqDTO chatReqDTO) {
        log.info("收到聊天请求：{}，对话ID：{}", chatReqDTO.getMessage(), chatReqDTO.getConversationId());
        ChatRespDTO response = chatService.sendAChat(chatReqDTO.getMessage(), chatReqDTO.getConversationId());
        return ResponseEntity.ok(response);
    }

    /**
     * 流式问答接口（SSE）。
     * 主流程：编排 -> 模型流式回调 -> 事件透传 -> 会话落库。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatReqDTO chatReqDTO) {
        boolean deepThinking = Boolean.TRUE.equals(chatReqDTO.getDeepThinking());
        log.info("收到流式聊天请求：{}，对话ID：{}，deepThinking={}",
                chatReqDTO.getMessage(), chatReqDTO.getConversationId(), deepThinking);
        SseEmitter emitter = new SseEmitter(chatFlowProperties.getSseTimeoutMillis());
        AtomicBoolean streamClosed = new AtomicBoolean(false);

        chatStreamExecutor.submit(() -> {
            try {
                String message = chatReqDTO.getMessage();
                try {
                    emitter.send(SseEmitter.event().name("ack").data("{\"stage\":\"orchestrate\"}"));
                } catch (Exception e) {
                    log.warn("chat-stream|发送 ack 失败", e);
                }
                OrchestrationResult orchestration = chatOrchestratorService.orchestrate(message, chatReqDTO.getConversationId());
                String conversationId = orchestration.getConversationId();
                long llmStart = System.currentTimeMillis();
                String referencesPayload = buildReferencesPayload(orchestration.getRetrievedChunks());

                emitter.send(SseEmitter.event().name("meta").data(
                        String.format("{\"requestId\":\"%s\",\"conversationId\":\"%s\",\"orchestrateMs\":%d,\"model\":\"%s\",\"deepThinking\":%s}",
                                orchestration.getRequestId(), conversationId, orchestration.getTotalCostMs(), defaultChatModel,
                                deepThinking)));
                emitter.send(SseEmitter.event().name("queryRewrite").data(orchestration.getRewrittenQuery()));
                emitter.send(SseEmitter.event().name("intent").data(orchestration.getIntent()));

                AtomicBoolean terminalSent = new AtomicBoolean(false);
                StreamCallback callback = new StreamCallback() {
                    private boolean firstTokenSent = false;
                    private StringBuilder fullResponse = new StringBuilder();
                    private StringBuilder reasoningResponse = new StringBuilder();
                    private boolean referencesSent = false;
                    private int streamedExtractLen = 0;

                    private void sendReferencesOnce() {
                        if (referencesSent) {
                            return;
                        }
                        try {
                            emitter.send(SseEmitter.event().name("references").data(referencesPayload));
                            referencesSent = true;
                        } catch (Exception e) {
                            log.warn("发送参考资料事件失败", e);
                        }
                    }

                    @Override
                    public void onFirstToken() {
                        if (streamClosed.get()) {
                            return;
                        }
                        if (!firstTokenSent) {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("first-token")
                                        .data("TTFB: " + (System.currentTimeMillis() - llmStart)));
                                firstTokenSent = true;
                                log.info("首包已发送");
                            } catch (Exception e) {
                                log.error("发送首包失败", e);
                            }
                        }
                    }

                    @Override
                    public void onReasoning(String reasoning) {
                        if (streamClosed.get()) {
                            return;
                        }
                        try {
                            String reasoningChunk = reasoning == null ? "" : reasoning;
                            if (deepThinking) {
                                emitter.send(SseEmitter.event()
                                        .name("reasoning")
                                        .data(reasoningChunk));
                            }
                            reasoningResponse.append(reasoningChunk);
                            if (!deepThinking) {
                                String streamedAnswer = SiliconFlowAssistantText
                                        .extractFinalAnswerForStreaming(reasoningResponse.toString());
                                if (!streamedAnswer.isBlank() && streamedAnswer.length() > streamedExtractLen) {
                                    String delta = streamedAnswer.substring(streamedExtractLen);
                                    if (!delta.isEmpty()) {
                                        emitter.send(SseEmitter.event()
                                                .name("content")
                                                .data(delta));
                                        fullResponse.append(delta);
                                        streamedExtractLen = streamedAnswer.length();
                                        sendReferencesOnce();
                                        debugLog("stream:onReasoning:extract-stream-append", "H18", Map.of(
                                                "deltaLen", delta.length(),
                                                "streamedLen", streamedExtractLen
                                        ));
                                    }
                                }
                            }
                            // #region agent log
                            debugLog("stream:onReasoning:append", "H4", Map.of(
                                    "chunkLen", reasoningChunk.length(),
                                    "accLen", reasoningResponse.length(),
                                    "chunkPreview", reasoningChunk.substring(0, Math.min(reasoningChunk.length(), 80))
                            ));
                            // #endregion
                        } catch (Exception e) {
                            log.error("发送思考内容失败", e);
                        }
                    }

                    @Override
                    public void onContent(String content) {
                        if (streamClosed.get()) {
                            return;
                        }
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("content")
                                    .data(content));
                            fullResponse.append(content);
                            sendReferencesOnce();
                            // #region agent log
                            debugLog("stream:onContent:append", "H4", Map.of(
                                    "chunkLen", content == null ? 0 : content.length(),
                                    "accLen", fullResponse.length(),
                                    "chunkPreview", content == null ? "" : content.substring(0, Math.min(content.length(), 80))
                            ));
                            // #endregion
                        } catch (Exception e) {
                            log.error("发送内容失败", e);
                        }
                    }

                    @Override
                    public void onComplete() {
                        if (streamClosed.get()) {
                            return;
                        }
                        if (!terminalSent.compareAndSet(false, true)) {
                            return;
                        }
                        try {
                            String responseContent = fullResponse.toString();
                            boolean fallbackAnswerUsed = false;
                            String reasoningText = reasoningResponse.toString();
                            int revisedDraftIdx = reasoningText.indexOf("Revised Draft:");
                            // #region agent log
                            debugLog("stream:onComplete:revised-draft-check", "H18", Map.of(
                                    "reasoningLen", reasoningText.length(),
                                    "revisedDraftIdx", revisedDraftIdx
                            ));
                            // #endregion
                            if (responseContent.isBlank() && revisedDraftIdx >= 0) {
                                String thinkingPart = reasoningText.substring(0, revisedDraftIdx).trim();
                                String answerPart = reasoningText.substring(revisedDraftIdx + "Revised Draft:".length()).trim();
                                if (deepThinking) {
                                    emitter.send(SseEmitter.event().name("reasoning-reset").data(thinkingPart));
                                }
                                if (!answerPart.isBlank()) {
                                    responseContent = answerPart;
                                    fallbackAnswerUsed = true;
                                    emitter.send(SseEmitter.event().name("content").data(answerPart));
                                }
                                // #region agent log
                                debugLog("stream:onComplete:revised-draft-split", "H18", Map.of(
                                        "thinkingLen", thinkingPart.length(),
                                        "answerLen", answerPart.length()
                                ));
                                // #endregion
                            }
                            if (responseContent.isBlank() && reasoningResponse.length() > 0 && !deepThinking) {
                                String extracted = SiliconFlowAssistantText.extractFinalAnswer(reasoningText);
                                if (extracted.isBlank()) {
                                    extracted = "抱歉，本次未生成可展示答案，请重试一次。";
                                }
                                responseContent = extracted;
                                fallbackAnswerUsed = true;
                                emitter.send(SseEmitter.event()
                                        .name("content")
                                        .data(extracted));
                                debugLog("stream:onComplete:reasoning-extract-used", "H17", Map.of(
                                        "answerLen", extracted.length(),
                                        "reasoningLen", reasoningResponse.length()
                                ));
                            }
                            if (responseContent.isBlank() && reasoningResponse.length() > 0 && deepThinking) {
                                // #region agent log
                                debugLog("stream:onComplete:fallback-chat-trigger", "H17", Map.of(
                                        "reasoningLen", reasoningResponse.length(),
                                        "conversationId", conversationId
                                ));
                                // #endregion
                                String fallbackAnswer = "";
                                try {
                                    ChatRespDTO fallbackResp = chatModelService.chat(message, orchestration.getFullContext());
                                    fallbackAnswer = fallbackResp == null || fallbackResp.getMessage() == null ? ""
                                            : fallbackResp.getMessage().trim();
                                } catch (Exception fallbackEx) {
                                    debugLog("stream:onComplete:fallback-chat-error", "H17", Map.of(
                                            "errorType", fallbackEx.getClass().getName(),
                                            "errorMessage", fallbackEx.getMessage() == null ? "" : fallbackEx.getMessage()
                                    ));
                                }
                                if (fallbackAnswer.isBlank()) {
                                    fallbackAnswer = SiliconFlowAssistantText.extractFinalAnswer(reasoningResponse.toString());
                                }
                                if (!fallbackAnswer.isBlank()) {
                                    responseContent = fallbackAnswer;
                                    fallbackAnswerUsed = true;
                                    emitter.send(SseEmitter.event()
                                            .name("content")
                                            .data(fallbackAnswer));
                                }
                                // #region agent log
                                debugLog("stream:onComplete:fallback-chat-result", "H17", Map.of(
                                        "fallbackAnswerLen", fallbackAnswer.length(),
                                        "fallbackAnswerPreview", fallbackAnswer.substring(0, Math.min(fallbackAnswer.length(), 120))
                                ));
                                // #endregion
                            }
                            if (responseContent.isBlank()) {
                                responseContent = "抱歉，本次未生成可展示答案，请重试一次。";
                                emitter.send(SseEmitter.event()
                                        .name("content")
                                        .data(responseContent));
                            }
                            sendReferencesOnce();
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data(""));
                            emitter.complete();
                            log.info("流式输出完成");

                            // 添加助手回复到对话
                            // #region agent log
                            debugLog("stream:onComplete:before-save", "H5", Map.of(
                                    "responseLen", responseContent.length(),
                                    "responsePreview", responseContent.substring(0, Math.min(responseContent.length(), 200)),
                                    "reasoningLen", reasoningResponse.length(),
                                    "fallbackAnswerUsed", fallbackAnswerUsed,
                                    "deepThinking", deepThinking
                            ));
                            // #endregion
                            conversationService.addMessage(conversationId, "default", "assistant", responseContent);
                            log.info("chat-stream|requestId={}|conversationId={}|llmCostMs={}|replyLen={}",
                                    orchestration.getRequestId(), conversationId, System.currentTimeMillis() - llmStart, responseContent.length());

                            // 更新对话标题（如果是第一条消息）
                            if (conversationService.getConversationMessages(conversationId).size() == 2) {
                                conversationService.updateConversationTitle(conversationId,
                                        responseContent.length() > 20 ? responseContent.substring(0, 20) + "..."
                                                : responseContent);
                            }

                        } catch (Exception e) {
                            log.error("发送完成事件失败", e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (streamClosed.get()) {
                            return;
                        }
                        // #region agent log
                        debugLog("stream:onError:entered", "H9", Map.of(
                                "errorType", error == null ? "null" : error.getClass().getName(),
                                "errorMessage", error == null || error.getMessage() == null ? "" : error.getMessage()
                        ));
                        // #endregion
                        if (!terminalSent.compareAndSet(false, true)) {
                            return;
                        }
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(error.getMessage()));
                            emitter.completeWithError(error);
                            log.error("流式输出错误", error);
                        } catch (Exception e) {
                            log.error("发送错误事件失败", e);
                        }
                    }
                };
                // 调用大模型进行流式输出
                chatModelService.streamChat(message, orchestration.getFullContext(), deepThinking, callback);

            } catch (Exception e) {
                log.error("流式聊天处理失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("发送错误失败", ex);
                }
            }
        });

        emitter.onCompletion(() -> {
            streamClosed.set(true);
            // #region agent log
            debugLog("emitter:onCompletion", "H10", Map.of());
            // #endregion
            log.info("SSE连接完成");
        });

        emitter.onTimeout(() -> {
            streamClosed.set(true);
            // #region agent log
            debugLog("emitter:onTimeout", "H10", Map.of());
            // #endregion
            log.warn("SSE连接超时");
            emitter.complete();
        });

        emitter.onError((ex) -> {
            streamClosed.set(true);
            // #region agent log
            debugLog("emitter:onError", "H10", Map.of(
                    "errorType", ex == null ? "null" : ex.getClass().getName(),
                    "errorMessage", ex == null || ex.getMessage() == null ? "" : ex.getMessage()
            ));
            // #endregion
            log.error("SSE连接错误", ex);
        });

        return emitter;
    }

    /**
     * 调试日志写入（文件方式）。
     */
    private void debugLog(String message, String hypothesisId, Map<String, Object> data) {
        try {
            String payload = "{\"sessionId\":\"a4f8e1\",\"runId\":\"pre-fix\",\"hypothesisId\":\"" + hypothesisId +
                    "\",\"location\":\"ChatController.java\",\"message\":\"" + message.replace("\"", "\\\"") +
                    "\",\"data\":" + new com.google.gson.Gson().toJson(data) +
                    ",\"timestamp\":" + System.currentTimeMillis() + "}";
            Files.writeString(Path.of(DEBUG_LOG_PATH), payload + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    /**
     * 将检索结果转换为 references 事件 JSON。
     */
    private String buildReferencesPayload(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "{\"count\":0,\"items\":[]}";
        }
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        int index = 1;
        for (RetrievedChunk chunk : chunks) {
            String title = chunk.getDocTitle() == null || chunk.getDocTitle().isBlank() ? "未命名文档" : chunk.getDocTitle();
            String docId = chunk.getDocId() == null ? "" : chunk.getDocId();
            String content = chunk.getContent() == null ? "" : chunk.getContent();
            if (content.length() > 220) {
                content = content.substring(0, 220) + "...";
            }
            content = escapeJson(content);
            title = escapeJson(title);
            docId = escapeJson(docId);
            joiner.add(String.format("{\"index\":%d,\"title\":\"%s\",\"docId\":\"%s\",\"score\":%.4f,\"content\":\"%s\"}",
                    index++, title, docId, chunk.getScore(), content));
        }
        return String.format("{\"count\":%d,\"items\":%s}", chunks.size(), joiner);
    }

    /**
     * 轻量 JSON 转义。
     */
    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
