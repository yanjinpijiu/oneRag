package com.onerag.chat.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.onerag.chat.service.IntentRecognitionService;
import com.onerag.chat.util.SiliconFlowAssistantText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * 基于 SiliconFlow 的意图识别服务实现类
 * 调用 SiliconFlow API 识别用户查询的具体意图，用于后续的检索策略选择
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiliconFlowIntentRecognitionService implements IntentRecognitionService {
    private static final String DEBUG_LOG_PATH = "debug-a4f8e1.log";

    /**
     * SiliconFlow API 密钥，从配置文件读取
     */
    @Value("${ai.providers.siliconflow.api-key}")
    private String apiKey;

    /**
     * SiliconFlow API 基础 URL，从配置文件读取
     */
    @Value("${ai.providers.siliconflow.url}")
    private String apiUrl;

    @Value("${rag.chat.intent-timeout-ms:8000}")
    private int intentTimeoutMs;

    @Value("${ai.chat.rewrite-intent-model:Qwen/Qwen3.5-35B-A3B}")
    private String chatModel;
    @Value("${ai.chat.thinking-budget.intent:512}")
    private int intentThinkingBudget;

    /**
     * HTTP 客户端，用于发送 HTTP 请求到 SiliconFlow API
     * 配置了连接、读取、写入超时时间均为 30 秒
     */
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    /**
     * Gson 工具，用于 JSON 数据的序列化和反序列化
     */
    private final Gson gson = new Gson();

    /**
     * 识别用户意图
     * 根据用户的查询和对话上下文，识别用户的具体意图类型
     *
     * @param query   用户查询
     * @param context 对话上下文
     * @return 识别到的意图描述，如果失败则返回"通用查询"
     */
    @Override
    public String recognizeIntent(String query, String context) {
        try {
            // 1. 构建系统提示词
            // 定义 AI 助手的角色和任务要求
            String systemPrompt = "你是一个专业的意图识别助手。根据用户的查询和对话上下文，识别用户的具体意图。\n"
                    + "要求：\n"
                    + "1. 识别用户的核心需求\n"
                    + "2. 确定用户的具体意图类型\n"
                    + "3. 输出简洁明了的意图描述\n"
                    + "4. 只返回意图识别结果，不要添加任何解释或前缀\n";

            //  2. 构建 API 请求体
            // 创建 JSON 对象作为请求体
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", chatModel.trim());

            // 创建消息数组
            JsonArray messages = new JsonArray();

            // 添加系统消息（定义 AI 角色）
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);

            // 添加用户消息（包含查询和上下文）
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", "查询：" + query + "\n上下文：" + context);
            messages.add(userMessage);

            // 将消息数组添加到请求体
            requestBody.add("messages", messages);
            // 设置温度参数为 0.1，降低随机性，使输出更稳定
            requestBody.addProperty("temperature", 0.1);
            requestBody.addProperty("max_tokens", 512);
            requestBody.addProperty("thinking_budget", Math.max(1, intentThinkingBudget));
            // #region agent log
            debugLog("intent:request-built", "pre-fix", "I1", Map.of(
                    "queryLen", query == null ? 0 : query.length(),
                    "contextLen", context == null ? 0 : context.length(),
                    "model", chatModel == null ? "" : chatModel.trim(),
                    "maxTokens", 512,
                    "thinkingBudget", Math.max(1, intentThinkingBudget),
                    "timeoutMs", Math.max(intentTimeoutMs, 20_000)
            ));
            // #endregion

            // 3. 创建并发送 HTTP 请求
            // 构建完整的 HTTP 请求
            Request request = new Request.Builder()
                    // 请求 URL：基础 URL + /chat/completions 端点
                    .url(apiUrl + "/chat/completions")
                    // 认证头：Bearer Token 方式
                    .header("Authorization", "Bearer " + apiKey)
                    // 内容类型：JSON
                    .header("Content-Type", "application/json")
                    // POST 方法，请求体为 JSON 格式
                    .post(RequestBody.create(gson.toJson(requestBody), MediaType.parse("application/json")))
                    .build();

            // 同步执行：connect/read/write/call 统一到同一上限，避免继承的 connect 8s 先于 read 触发；并行改写+意图时厂商排队易超过 12s
            int ms = Math.max(intentTimeoutMs, 20_000);
            OkHttpClient timedClient = okHttpClient.newBuilder()
                    .connectTimeout(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .callTimeout(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .writeTimeout(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build();
            Response response = timedClient.newCall(request).execute();
            // #region agent log
            debugLog("intent:http-response", "pre-fix", "I2", Map.of(
                    "httpCode", response.code(),
                    "successful", response.isSuccessful()
            ));
            // #endregion

            // 4. 处理 HTTP 响应
            // 检查响应状态码是否成功（2xx）
            if (!response.isSuccessful()) {
                // 记录错误日志，包括状态码和响应体
                log.error("Intent recognition HTTP error: status={}, body={}", response.code(),
                        response.body() != null ? response.body().string() : "null");
                // HTTP 请求失败时，返回默认意图"通用查询"
                return "通用查询";
            }

            // 5. 解析 AI 响应
            // 获取响应体字符串
            String responseBody = response.body().string();
            // 使用 Gson 将 JSON 字符串解析为 JsonObject
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            // #region agent log
            debugLog("intent:response-parsed", "pre-fix", "I3", Map.of(
                    "hasChoices", jsonResponse.has("choices"),
                    "bodyPreview", responseBody.substring(0, Math.min(responseBody.length(), 200))
            ));
            // #endregion

            // 检查响应格式是否正确：包含 choices 数组
            if (jsonResponse.has("choices") && jsonResponse.get("choices").isJsonArray()) {
                // 获取 choices 数组
                JsonArray choices = jsonResponse.getAsJsonArray("choices");
                // 确保 choices 数组非空
                if (choices.size() > 0) {
                    // 获取第一个选择（通常 AI 会返回多个候选，这里只取最佳）
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    // 检查 choice 是否包含 message 对象
                    if (choice.has("message") && choice.get("message").isJsonObject()) {
                        JsonObject message = choice.getAsJsonObject("message");
                        String rawContent = SiliconFlowAssistantText.fromMessageContentOnly(message);
                        String reasoning = SiliconFlowAssistantText.fromMessage(message);
                        String extracted = rawContent;
                        if (extracted == null || extracted.isBlank()) {
                            extracted = SiliconFlowAssistantText.extractFinalAnswer(reasoning);
                        }
                        String finishReason = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                                ? choice.get("finish_reason").getAsString()
                                : "";
                        // #region agent log
                        debugLog("intent:choice-evaluated", "pre-fix", "I4", Map.of(
                                "finishReason", finishReason,
                                "contentLen", rawContent == null ? 0 : rawContent.length(),
                                "reasoningLen", reasoning == null ? 0 : reasoning.length(),
                                "extractedLen", extracted == null ? 0 : extracted.length(),
                                "reasoningPreview", reasoning == null ? "" : reasoning.substring(0, Math.min(reasoning.length(), 160))
                        ));
                        // #endregion
                        if (rawContent.isBlank() && choice.has("finish_reason")) {
                            log.warn("【意图识别】模型输出为空 finish_reason={}", choice.get("finish_reason"));
                        }
                        log.info("【意图识别】输入查询：{}", query);
                        log.info("【意图识别】AI 原始响应：\"{}\"", rawContent);
                        log.info("【意图识别】Trim 后结果：\"{}\"", rawContent.trim());
                        log.info("【意图识别】最终返回：\"{}\"", rawContent.isBlank() ? "(空字符串，使用通用查询)" : rawContent.trim());
                        // #region agent log
                        debugLog("intent:return", "pre-fix", "I5", Map.of(
                                "returnedFallback", rawContent == null || rawContent.isBlank(),
                                "returnedValuePreview", (rawContent == null || rawContent.isBlank()) ? "通用查询" : rawContent.trim()
                        ));
                        // #endregion
                        return rawContent.isBlank() ? "通用查询" : rawContent.trim();
                    }
                }
            }

            //7. 异常处理
            // 如果响应格式不符合预期，记录警告日志
            log.warn("意图识别响应格式异常：{}", responseBody);
            // 返回默认意图"通用查询"
            return "通用查询";

        } catch (IOException e) {
            // 捕获 IO 异常（网络问题等）
            log.error("意图识别失败", e);
            // #region agent log
            debugLog("intent:io-exception", "pre-fix", "I2", Map.of(
                    "errorType", e.getClass().getName(),
                    "errorMessage", e.getMessage() == null ? "" : e.getMessage()
            ));
            // #endregion
            // 异常时返回默认意图"通用查询"
            return "通用查询";
        }
    }

    private void debugLog(String message, String runId, String hypothesisId, Map<String, Object> data) {
        try {
            String payload = new Gson().toJson(Map.of(
                    "sessionId", "a4f8e1",
                    "runId", runId,
                    "hypothesisId", hypothesisId,
                    "location", "SiliconFlowIntentRecognitionService.java",
                    "message", message,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            ));
            Files.writeString(Path.of(DEBUG_LOG_PATH), payload + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
