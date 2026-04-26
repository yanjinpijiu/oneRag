package com.onerag.chat.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.onerag.chat.DTO.resp.ChatRespDTO;
import com.onerag.chat.util.SiliconFlowAssistantText;
import com.onerag.chat.service.ChatModelService;
import com.onerag.chat.service.StreamCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.onerag.chat.DTO.PromptDTO.PROMPT_TEMPLATE;

/**
 * SiliconFlow 聊天模型实现。
 * 提供同步与 SSE 两种调用方式，并统一处理 content/reasoning 兼容逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowChatModelService implements ChatModelService {
    private static final String DEBUG_LOG_PATH = "debug-a4f8e1.log";
    private static final AtomicInteger SSE_PARSE_WARN_COUNTER = new AtomicInteger(0);

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    @Value("${ai.providers.siliconflow.api-key}")
    private String apiKey;

    @Value("${ai.providers.siliconflow.url}")
    private String baseUrl;

    @Value("${ai.providers.siliconflow.endpoints.chat}")
    private String chatEndpoint;

    @Value("${ai.chat.default-model}")
    private String defaultModel;
    private static final int STREAM_MAX_TOKENS = 4096;
    @Value("${ai.chat.thinking-budget.chat:1024}")
    private int chatThinkingBudget;
    @Value("${ai.chat.thinking-budget.stream-default:1024}")
    private int streamThinkingBudgetDefault;
    @Value("${ai.chat.thinking-budget.stream-deep:2048}")
    private int streamThinkingBudgetDeep;

    /**
     * 同步调用模型，优先读取 content；
     * 当 content 为空时，从 reasoning 中提取最终可展示答案。
     */
    @Override
    public ChatRespDTO chat(String message, String context) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", defaultModel);
            request.put("messages", buildMessages(message, context));
            request.put("temperature", 0.1);
            request.put("max_tokens", STREAM_MAX_TOKENS);
            request.put("thinking_budget", Math.max(1, chatThinkingBudget));

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + chatEndpoint)
                    .post(RequestBody.create(gson.toJson(request), MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            OkHttpClient streamClient = httpClient.newBuilder()
                    .readTimeout(Duration.ofSeconds(90))
                    .build();
            try (Response response = streamClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("SiliconFlow chat HTTP error: status={}, body={}", response.code(), errorBody);
                    throw new RuntimeException("调用 SiliconFlow Chat 失败: HTTP " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject root = gson.fromJson(responseBody, JsonObject.class);

                if (root.has("choices") && root.getAsJsonArray("choices").size() > 0) {
                    JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
                    if (choice.has("message") && choice.get("message").isJsonObject()) {
                        JsonObject messageObj = choice.getAsJsonObject("message");
                        String text = SiliconFlowAssistantText.fromMessageContentOnly(messageObj);
                        if (text.isBlank()) {
                            String reasoning = SiliconFlowAssistantText.fromMessage(messageObj);
                            text = SiliconFlowAssistantText.extractFinalAnswer(reasoning);
                        }
                        if (!text.isBlank()) {
                            log.info("成功调用大模型，返回内容长度：{}", text.length());
                            return new ChatRespDTO(text, null);
                        }
                    }
                }

                log.warn("SiliconFlow chat 返回空答案，使用兜底文案");
                return new ChatRespDTO("抱歉，本次未生成可展示答案，请重试一次。", null);
            }
        } catch (IOException e) {
            log.error("调用 SiliconFlow Chat 失败", e);
            throw new RuntimeException("调用 SiliconFlow Chat 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用模型，并把增量 token 转发给上层回调。
     */
    @Override
    public void streamChat(String message, String context, boolean deepThinking, StreamCallback callback) {
        try {
            int thinkingBudget = Math.max(1, deepThinking ? streamThinkingBudgetDeep : streamThinkingBudgetDefault);
            // #region agent log
            debugLog("streamChat:entry", "H1", Map.of(
                    "model", defaultModel,
                    "messageLen", message == null ? 0 : message.length(),
                    "contextLen", context == null ? 0 : context.length(),
                    "maxTokens", STREAM_MAX_TOKENS,
                    "thinkingBudget", thinkingBudget,
                    "deepThinking", deepThinking
            ));
            // #endregion
            Map<String, Object> request = new HashMap<>();
            request.put("model", defaultModel);
            request.put("messages", buildMessages(message, context));
            request.put("temperature", 0.1);
            request.put("max_tokens", STREAM_MAX_TOKENS);
            request.put("thinking_budget", thinkingBudget);
            request.put("stream", true);

            Request httpRequest = new Request.Builder()
                    .url(baseUrl + chatEndpoint)
                    .post(RequestBody.create(gson.toJson(request), MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            // #region agent log
            debugLog("streamChat:http-execute:start", "H7", Map.of(
                    "url", baseUrl + chatEndpoint,
                    "model", defaultModel
            ));
            // #endregion
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                // #region agent log
                debugLog("streamChat:http-execute:response", "H7", Map.of(
                        "code", response.code(),
                        "successful", response.isSuccessful()
                ));
                // #endregion
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("SiliconFlow stream chat HTTP error: status={}, body={}", response.code(), errorBody);
                    callback.onError(new RuntimeException(
                            "调用 SiliconFlow Chat 失败: HTTP " + response.code() + " - " + errorBody));
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    callback.onError(new RuntimeException("SiliconFlow stream chat response body is null"));
                    return;
                }

                BufferedSource source = body.source();
                boolean firstToken = true;
                AtomicBoolean completed = new AtomicBoolean(false);
                int nonDataLineSamples = 0;
                int reasoningChars = 0;
                int contentChars = 0;

                while (!completed.get()) {
                    String line = source.readUtf8Line();
                    if (line == null) {
                        // #region agent log
                        debugLog("streamChat:sse-line-null", "H8", Map.of());
                        // #endregion
                        break;
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                    if (!line.startsWith("data: ") && nonDataLineSamples < 5) {
                        nonDataLineSamples++;
                        // #region agent log
                        debugLog("streamChat:sse-non-data-line", "H8", Map.of(
                                "linePreview", line.substring(0, Math.min(line.length(), 120))
                        ));
                        // #endregion
                    }

                    try {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) {
                                completeOnce(callback, completed);
                                break;
                            }

                            JsonObject root = gson.fromJson(data, JsonObject.class);
                            if (root.has("choices") && root.getAsJsonArray("choices").size() > 0) {
                                JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
                                if (choice.has("delta")) {
                                    JsonObject delta = choice.getAsJsonObject("delta");
                                    // #region agent log
                                    debugLog("streamChat:delta-keys", "H2", Map.of(
                                            "hasContent", delta.has("content"),
                                            "hasReasoningContent", delta.has("reasoning_content"),
                                            "hasThinking", delta.has("thinking")
                                    ));
                                    // #endregion
                                    String contentPiece = extractDeltaText(delta.get("content"));
                                    String reasoningPiece = extractDeltaText(delta.get("reasoning_content"));
                                    if ((contentPiece == null || contentPiece.isEmpty()) && delta.has("content")) {
                                        // #region agent log
                                        debugLog("streamChat:content-raw-shape", "H15", Map.of(
                                                "contentElementType", delta.get("content").isJsonArray() ? "array"
                                                        : delta.get("content").isJsonObject() ? "object"
                                                        : delta.get("content").isJsonPrimitive() ? "primitive" : "other",
                                                "contentRawPreview", delta.get("content").toString().substring(0,
                                                        Math.min(delta.get("content").toString().length(), 200))
                                        ));
                                        // #endregion
                                    }
                                    // #region agent log
                                    if (contentPiece != null || reasoningPiece != null) {
                                        debugLog("streamChat:delta-compare", "H6", Map.of(
                                                "contentLen", contentPiece == null ? 0 : contentPiece.length(),
                                                "reasoningLen", reasoningPiece == null ? 0 : reasoningPiece.length(),
                                                "contentPreview", contentPiece == null ? "" : contentPiece.substring(0, Math.min(contentPiece.length(), 80)),
                                                "reasoningPreview", reasoningPiece == null ? "" : reasoningPiece.substring(0, Math.min(reasoningPiece.length(), 80)),
                                                "sameText", contentPiece != null && contentPiece.equals(reasoningPiece)
                                        ));
                                    }
                                    // #endregion
                                    if (reasoningPiece != null && !reasoningPiece.isEmpty()) {
                                        reasoningChars += reasoningPiece.length();
                                        // #region agent log
                                        debugLog("streamChat:emit-reasoning", "H3", Map.of(
                                                "pieceLen", reasoningPiece.length(),
                                                "piecePreview", reasoningPiece.substring(0, Math.min(reasoningPiece.length(), 80))
                                        ));
                                        // #endregion
                                        if (firstToken) {
                                            callback.onFirstToken();
                                            firstToken = false;
                                        }
                                        callback.onReasoning(reasoningPiece);
                                    }
                                    if (contentPiece != null && !contentPiece.isEmpty()) {
                                        contentChars += contentPiece.length();
                                        // #region agent log
                                        debugLog("streamChat:emit-content", "H3", Map.of(
                                                "pieceLen", contentPiece.length(),
                                                "piecePreview", contentPiece.substring(0, Math.min(contentPiece.length(), 80))
                                        ));
                                        // #endregion
                                        if (firstToken) {
                                            callback.onFirstToken();
                                            firstToken = false;
                                        }
                                        callback.onContent(contentPiece);
                                    }
                                }
                                if (choice.has("finish_reason")) {
                                    String finishReason = choice.get("finish_reason").getAsString();
                                    // #region agent log
                                    debugLog("streamChat:finish-reason", "H15", Map.of(
                                            "finishReason", finishReason,
                                            "contentChars", contentChars,
                                            "reasoningChars", reasoningChars
                                    ));
                                    // #endregion
                                    if ("stop".equals(finishReason)) {
                                        completeOnce(callback, completed);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        int warnCount = SSE_PARSE_WARN_COUNTER.incrementAndGet();
                        if ((warnCount - 1) % 20 == 0) {
                            log.warn("解析 SSE 行失败(采样日志，第{}次) linePrefix={}",
                                    warnCount, line.length() > 120 ? line.substring(0, 120) : line, e);
                        }
                    }
                }

                if (!completed.get()) {
                    completeOnce(callback, completed);
                }
            }
        } catch (Exception e) {
            log.error("调用 SiliconFlow Stream Chat 失败", e);
            callback.onError(e);
        }
    }

    /**
     * 兼容 delta 的多种结构（字符串/数组/对象）并提取文本。
     */
    private static String extractDeltaText(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString();
            return (s == null || s.isEmpty()) ? null : s;
        }
        if (el.isJsonArray()) {
            JsonArray array = el.getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            for (JsonElement item : array) {
                String text = extractDeltaText(item);
                if (text != null) {
                    sb.append(text);
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text")) {
                return extractDeltaText(obj.get("text"));
            }
            if (obj.has("content")) {
                return extractDeltaText(obj.get("content"));
            }
        }
        return null;
    }

    /**
     * 保证 onComplete 只触发一次。
     */
    private void completeOnce(StreamCallback callback, AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            callback.onComplete();
        }
    }

    /**
     * 写入调试日志文件，辅助排查流式事件差异。
     */
    private void debugLog(String message, String hypothesisId, Map<String, Object> data) {
        try {
            String payload = gson.toJson(Map.of(
                    "sessionId", "a4f8e1",
                    "runId", "pre-fix",
                    "hypothesisId", hypothesisId,
                    "location", "SiliconFlowChatModelService.java",
                    "message", message,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            ));
            Files.writeString(Path.of(DEBUG_LOG_PATH), payload + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    /**
     * 组装 chat/completions 所需 messages 数组。
     */
    private List<Map<String, String>> buildMessages(String userMessage, String context) {
        List<Map<String, String>> messages = new ArrayList<>();
        // 系统提示
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", buildSystemPrompt(context, userMessage));
        messages.add(systemMessage);
        // 用户消息
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        // TODO 添加用户上下文
        return messages;
    }

    /**
     * 构建系统提示词：有 RAG 上下文时走模板，否侧走通用助手兜底提示。
     */
    private String buildSystemPrompt(String context, String userMessage) {
        if (context == null || context.isEmpty()) {
            return "你是一个叫”温州大学ai学长帅帅“的智能助手，目前没有检索到参考资料，请根据用户的问题提供准确、有帮助的回答，如果用户只想闲聊那就闲聊，如果你对用户问题不能回答，如实回答不知道。";
        }

        return PROMPT_TEMPLATE
                .replace("{{chunks}}", context);
    }
}
