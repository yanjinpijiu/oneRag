package com.onerag.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 聊天编排参数配置。
 * 对应 application.yml 中的 rag.chat.*。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.chat")
public class ChatFlowProperties {

    /** 是否启用查询改写。 */
    private boolean queryRewriteEnabled = true;

    /** 是否启用意图识别。 */
    private boolean intentEnabled = true;

    /** 是否启用检索增强。 */
    private boolean retrievalEnabled = true;

    /** 最终提示词总长度上限。 */
    private int maxContextChars = 4000;

    /** 最多保留多少个 RAG 片段。 */
    private int maxRagChunks = 5;

    /** RAG 片段总字符上限。 */
    private int maxRagContextChars = 3000;

    /** 检索召回数量（TopK）。 */
    private int retrievalTopK = 5;

    /** 是否启用 BM25 关键词检索。 */
    private boolean bm25Enabled = false;

    /** 改写/意图缓存过期时间。 */
    private long cacheTtlMillis = 60000L;

    /** SSE 连接超时时间。 */
    private long sseTimeoutMillis = 600_000L;

    /** 查询改写调用超时。 */
    private int rewriteTimeoutMs = 10_000;

    /** 意图识别调用超时。 */
    private int intentTimeoutMs = 10_000;

    /** 当问题长度不超过该阈值时跳过改写。 */
    private int queryRewriteSkipIfShorterThanOrEqual = 0;
}
