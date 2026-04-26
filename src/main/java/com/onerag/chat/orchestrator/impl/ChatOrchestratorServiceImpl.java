package com.onerag.chat.orchestrator.impl;

import com.onerag.chat.config.ChatFlowProperties;
import com.onerag.chat.orchestrator.ChatOrchestratorService;
import com.onerag.chat.orchestrator.OrchestrationResult;
import com.onerag.chat.orchestrator.PromptContextBuilder;
import com.onerag.chat.service.ConversationService;
import com.onerag.chat.service.IntentRecognitionService;
import com.onerag.chat.service.QueryRewriterService;
import com.onerag.document.dto.RetrievedChunk;
import com.onerag.document.service.BM25RetrieverService;
import com.onerag.document.service.MilvusRetrieverService;
import com.onerag.document.service.RetrievalFusionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

/**
 * 聊天编排实现。
 * 负责在模型生成前完成上下文构建、改写、意图识别与检索融合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestratorServiceImpl implements ChatOrchestratorService {

    private static final String DEFAULT_USER_ID = "default";
    private static final String DEBUG_LOG_PATH = "debug-a4f8e1.log";

    private final MilvusRetrieverService milvusRetrieverService;
    private final BM25RetrieverService bm25RetrieverService;
    private final RetrievalFusionService retrievalFusionService;
    private final QueryRewriterService queryRewriterService;
    private final IntentRecognitionService intentRecognitionService;
    private final ConversationService conversationService;
    private final PromptContextBuilder promptContextBuilder;
    private final ChatFlowProperties properties;

    private final ConcurrentHashMap<String, CacheEntry> rewriteCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry> intentCache = new ConcurrentHashMap<>();

    /**
     * 执行一次完整编排并产出统一结果对象。
     */
    @Override
    public OrchestrationResult orchestrate(String message, String conversationId, String userId) {
        long totalStart = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        String actualConversationId = ensureConversation(conversationId, message, userId);

        long contextStart = System.currentTimeMillis();
        String rawConversationContext = conversationService.buildContext(actualConversationId);
        // #region agent log
        debugLog("orchestrate:context-raw", "H12", Map.of(
                "incomingConversationId", conversationId == null ? "null" : conversationId,
                "actualConversationId", actualConversationId,
                "rawContextLen", rawConversationContext == null ? 0 : rawConversationContext.length(),
                "willCompress", rawConversationContext != null && rawConversationContext.length() > 1000
        ));
        // #endregion
        final String conversationContext = rawConversationContext.length() > 1000
                ? conversationService.compressContext(rawConversationContext)
                : rawConversationContext;
        logStep(requestId, actualConversationId, "build-context", contextStart);

        String rewrittenQuery = message;
        String intent = "通用查询";

        if (properties.isQueryRewriteEnabled() && properties.isIntentEnabled()) {
            // 改写与意图互不依赖：意图用用户原句即可判断闲聊/知识问答；检索仍用改写结果。墙钟时间≈max(改写,意图)。
            long parallelStart = System.currentTimeMillis();
            boolean skipRewrite = shouldSkipQueryRewrite(message);
            CompletableFuture<String> rewriteFuture = skipRewrite
                    ? CompletableFuture.completedFuture(message)
                    : CompletableFuture.supplyAsync(() -> cachedRewrite(message, conversationContext),
                            ForkJoinPool.commonPool());
            if (skipRewrite) {
                log.info("chat-step|requestId={}|conversationId={}|step=query-rewrite-skipped|reason=short-message|len={}",
                        requestId, actualConversationId, message.strip().length());
                log.info("query-rewrite|requestId={}|conversationId={}|skipped=true|finalQuery={}",
                        requestId, actualConversationId, preview(message));
                // #region agent log
                debugLog("orchestrate:query-rewrite-skipped", "I6", Map.of(
                        "requestId", requestId,
                        "conversationId", actualConversationId,
                        "finalQuery", preview(message)
                ));
                // #endregion
            }
            CompletableFuture<String> intentFuture = CompletableFuture.supplyAsync(
                    () -> cachedIntent(message, conversationContext), ForkJoinPool.commonPool());
            rewrittenQuery = rewriteFuture.handle((ok, ex) -> {
                if (ex != null) {
                    log.warn("改写失败，使用原句 requestId={}", requestId, ex);
                    return message;
                }
                return ok != null ? ok : message;
            }).join();
            intent = intentFuture.handle((ok, ex) -> {
                if (ex != null) {
                    log.warn("意图识别失败，使用通用查询 requestId={}", requestId, ex);
                    return "通用查询";
                }
                return ok != null && !ok.isEmpty() ? ok : "通用查询";
            }).join();
            // #region agent log
            debugLog("orchestrate:query-rewrite-parallel-done", "I6", Map.of(
                    "requestId", requestId,
                    "conversationId", actualConversationId,
                    "rewrittenQuery", preview(rewrittenQuery),
                    "intent", intent
            ));
            // #endregion
            logStep(requestId, actualConversationId, "rewrite-intent-parallel", parallelStart);
        } else if (properties.isQueryRewriteEnabled()) {
            long rewriteStart = System.currentTimeMillis();
            if (shouldSkipQueryRewrite(message)) {
                log.info("chat-step|requestId={}|conversationId={}|step=query-rewrite-skipped|reason=short-message|len={}",
                        requestId, actualConversationId, message.strip().length());
                rewrittenQuery = message;
                log.info("query-rewrite|requestId={}|conversationId={}|skipped=true|finalQuery={}",
                        requestId, actualConversationId, preview(rewrittenQuery));
            } else {
                rewrittenQuery = cachedRewrite(message, conversationContext);
            }
            logStep(requestId, actualConversationId, "rewrite-query", rewriteStart);
        } else if (properties.isIntentEnabled()) {
            long intentStart = System.currentTimeMillis();
            intent = cachedIntent(message, conversationContext);
            logStep(requestId, actualConversationId, "intent-recognition", intentStart);
        }

        List<RetrievedChunk> retrievedChunks = Collections.emptyList();
        if (properties.isRetrievalEnabled() && shouldRetrieve(intent)) {
            long retrievalStart = System.currentTimeMillis();
            List<RetrievedChunk> vectorResults = milvusRetrieverService.retrieve(rewrittenQuery);
            // #region agent log
            debugLog("orchestrate:retrieve-vector", "H16", Map.of(
                    "intent", intent == null ? "null" : intent,
                    "query", rewrittenQuery == null ? "" : rewrittenQuery,
                    "vectorCount", vectorResults == null ? 0 : vectorResults.size()
            ));
            // #endregion
            if (properties.isBm25Enabled()) {
                List<RetrievedChunk> bm25Results = bm25RetrieverService.retrieve(rewrittenQuery, properties.getRetrievalTopK());
                retrievedChunks = retrievalFusionService.fuseResults(vectorResults, bm25Results, properties.getRetrievalTopK());
            } else {
                retrievedChunks = vectorResults;
            }
            retrievedChunks = promptContextBuilder.trimChunks(retrievedChunks);
            // #region agent log
            debugLog("orchestrate:retrieve-trimmed", "H16", Map.of(
                    "trimmedCount", retrievedChunks == null ? 0 : retrievedChunks.size()
            ));
            // #endregion
            logStep(requestId, actualConversationId, "vector-retrieve", retrievalStart);
        } else {
            // #region agent log
            debugLog("orchestrate:retrieve-skipped", "H16", Map.of(
                    "intent", intent == null ? "null" : intent,
                    "retrievalEnabled", properties.isRetrievalEnabled()
            ));
            // #endregion
            log.info("chat-step|requestId={}|conversationId={}|step={}|reason={}",
                    requestId, actualConversationId, "vector-retrieve-skipped", intent);
        }

        String ragContext = promptContextBuilder.buildRagContext(retrievedChunks);
        String fullContext = promptContextBuilder.buildFullContext(conversationContext, ragContext);
        // #region agent log
        debugLog("orchestrate:query-rewrite-final-log", "I6", Map.of(
                "requestId", requestId,
                "conversationId", actualConversationId,
                "finalQuery", preview(rewrittenQuery),
                "infoEnabled", log.isInfoEnabled()
        ));
        // #endregion
        log.info("query-rewrite|requestId={}|conversationId={}|finalQuery={}",
                requestId, actualConversationId, preview(rewrittenQuery));
        // #region agent log
        debugLog("orchestrate:context-final", "H16", Map.of(
                "conversationContextLen", conversationContext == null ? 0 : conversationContext.length(),
                "ragContextLen", ragContext == null ? 0 : ragContext.length(),
                "fullContextLen", fullContext == null ? 0 : fullContext.length()
        ));
        // #endregion
        long totalCostMs = System.currentTimeMillis() - totalStart;
        log.info("chat-step|requestId={}|conversationId={}|step=orchestrate-total|costMs={}",
                requestId, actualConversationId, totalCostMs);

        return OrchestrationResult.builder()
                .requestId(requestId)
                .conversationId(actualConversationId)
                .originalMessage(message)
                .conversationContext(conversationContext)
                .rewrittenQuery(rewrittenQuery)
                .intent(intent)
                .retrievedChunks(retrievedChunks)
                .ragContext(ragContext)
                .fullContext(fullContext)
                .totalCostMs(totalCostMs)
                .build();
    }

    /**
     * 确保会话存在：空 conversationId 时创建新会话，否则复用并追加用户消息。
     */
    private String ensureConversation(String conversationId, String message, String userId) {
        String actualUserId = (userId == null || userId.isBlank()) ? DEFAULT_USER_ID : userId;
        if (conversationId == null || conversationId.isEmpty()) {
            String created = conversationService.createConversation(actualUserId, message);
            // #region agent log
            debugLog("orchestrate:ensureConversation:new", "H11", Map.of(
                    "incomingConversationId", conversationId == null ? "null" : conversationId,
                    "actualConversationId", created,
                    "messageLen", message == null ? 0 : message.length()
            ));
            // #endregion
            return created;
        }
        conversationService.checkConversationOwner(conversationId, actualUserId);
        conversationService.addMessage(conversationId, actualUserId, "user", message);
        // #region agent log
        debugLog("orchestrate:ensureConversation:reuse", "H11", Map.of(
                "incomingConversationId", conversationId,
                "actualConversationId", conversationId,
                "messageLen", message == null ? 0 : message.length()
        ));
        // #endregion
        return conversationId;
    }

    /**
     * 查询改写缓存。
     */
    private String cachedRewrite(String message, String context) {
        String key = message + "|" + context.hashCode();
        CacheEntry cached = rewriteCache.get(key);
        if (cached != null && !cached.isExpired()) {
            log.info("query-rewrite|cache=hit|input={}|output={}",
                    preview(message), preview(cached.value));
            return cached.value;
        }
        long start = System.currentTimeMillis();
        String value = queryRewriterService.rewriteQuery(message, context);
        long costMs = System.currentTimeMillis() - start;
        log.info("query-rewrite|cache=miss|costMs={}|input={}|output={}",
                costMs, preview(message), preview(value));
        rewriteCache.put(key, new CacheEntry(value, System.currentTimeMillis() + properties.getCacheTtlMillis()));
        return value;
    }

    /**
     * 意图识别缓存。
     */
    private String cachedIntent(String query, String context) {
        String key = query + "|" + context.hashCode();
        CacheEntry cached = intentCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }
        String value = intentRecognitionService.recognizeIntent(query, context);
        intentCache.put(key, new CacheEntry(value, System.currentTimeMillis() + properties.getCacheTtlMillis()));
        return value;
    }

    /**
     * 短问题可直接跳过改写，减少延迟。
     */
    private boolean shouldSkipQueryRewrite(String message) {
        int max = properties.getQueryRewriteSkipIfShorterThanOrEqual();
        return max > 0 && message != null && message.strip().length() <= max;
    }

    /**
     * 根据意图判定是否需要走知识检索。
     */
    private boolean shouldRetrieve(String intent) {
        if (intent == null) {
            return true;
        }
        String normalized = intent.toLowerCase();
        return !(normalized.contains("闲聊") || normalized.contains("问候")
                || normalized.contains("chat") || normalized.contains("greeting"));
    }

    /**
     * 输出统一步骤耗时日志。
     */
    private void logStep(String requestId, String conversationId, String step, long start) {
        log.info("chat-step|requestId={}|conversationId={}|step={}|costMs={}",
                requestId, conversationId, step, System.currentTimeMillis() - start);
    }

    /**
     * 日志预览：避免完整输出超长文本污染控制台。
     */
    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\n", "\\n").replace("\r", "\\r");
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    /**
     * 简单内存缓存条目。
     */
    private static class CacheEntry {
        private final String value;
        private final long expireAt;

        private CacheEntry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    /**
     * 写入调试日志文件。
     */
    private void debugLog(String message, String hypothesisId, Map<String, Object> data) {
        try {
            String payload = "{\"sessionId\":\"a4f8e1\",\"runId\":\"pre-fix\",\"hypothesisId\":\"" + hypothesisId +
                    "\",\"location\":\"ChatOrchestratorServiceImpl.java\",\"message\":\"" + message.replace("\"", "\\\"") +
                    "\",\"data\":" + new com.google.gson.Gson().toJson(data) +
                    ",\"timestamp\":" + System.currentTimeMillis() + "}";
            Files.writeString(Path.of(DEBUG_LOG_PATH), payload + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
