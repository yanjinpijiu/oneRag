package com.onerag.chat.orchestrator;

import com.onerag.document.dto.RetrievedChunk;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 一次问答编排的标准输出对象。
 * 用于在 Controller 与模型服务之间传递“可观测且可复用”的中间结果。
 */
@Data
@Builder
public class OrchestrationResult {

    /** 本次请求唯一标识，用于链路日志追踪。 */
    private String requestId;

    /** 当前会话ID。 */
    private String conversationId;

    /** 用户原始输入。 */
    private String originalMessage;

    /** 结构化的对话历史上下文。 */
    private String conversationContext;

    /** 经改写后的检索查询。 */
    private String rewrittenQuery;

    /** 意图识别结果。 */
    private String intent;

    /** 检索召回并裁剪后的片段集合。 */
    private List<RetrievedChunk> retrievedChunks;

    /** 仅包含检索片段的上下文文本。 */
    private String ragContext;

    /** 最终送入模型的完整上下文。 */
    private String fullContext;

    /** 编排阶段总耗时（毫秒）。 */
    private long totalCostMs;
}
