package com.onerag.chat.orchestrator;

import com.onerag.chat.config.ChatFlowProperties;
import com.onerag.document.dto.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * 提示词上下文构建器。
 * 负责裁剪检索片段并生成可直接注入 Prompt 的文本。
 */
@Component
public class PromptContextBuilder {

    private final ChatFlowProperties properties;

    public PromptContextBuilder(ChatFlowProperties properties) {
        this.properties = properties;
    }

    /**
     * 按数量与总长度限制裁剪检索片段。
     */
    public List<RetrievedChunk> trimChunks(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        int maxChunks = Math.max(1, properties.getMaxRagChunks());
        List<RetrievedChunk> limited = new ArrayList<>();
        int totalChars = 0;
        for (RetrievedChunk chunk : chunks) {
            if (limited.size() >= maxChunks) {
                break;
            }
            String content = chunk.getContent() == null ? "" : chunk.getContent();
            int next = totalChars + content.length();
            if (!limited.isEmpty() && next > properties.getMaxRagContextChars()) {
                break;
            }
            limited.add(chunk);
            totalChars = next;
        }
        return limited;
    }

    /**
     * 构建 RAG 片段正文与来源索引说明。
     */
    public String buildRagContext(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        // #region agent log
        try {
            StringJoiner sj = new StringJoiner(",", "[", "]");
            for (RetrievedChunk c : chunks) {
                sj.add(String.valueOf(c.getChunkIndex()));
            }
            String line = "{\"sessionId\":\"49ec09\",\"hypothesisId\":\"H1\",\"location\":\"PromptContextBuilder.buildRagContext\",\"message\":\"metadata chunk_index values (may repeat across docs)\",\"data\":{\"chunkIndices\":" + sj + ",\"count\":" + chunks.size() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n";
            Path logPath = Path.of("debug-49ec09.log");
            Files.writeString(logPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
        // #endregion

        StringBuilder sb = new StringBuilder();
        int n = chunks.size();
        for (int i = 0; i < n; i++) {
            RetrievedChunk chunk = chunks.get(i);
            int ref = i + 1;
            sb.append(String.format("[%d] 相似度:%.2f | 库内分块序号:%d\n%s\n\n",
                    ref, chunk.getScore(), chunk.getChunkIndex(),
                    chunk.getContent() == null ? "" : chunk.getContent()));
        }
        sb.append("### 参考资料来源\n");
        sb.append("回答时请用 [1]、[2] 等与下列编号对应；「依据与说明」中请写明各要点引自哪一条（含文档标题）。\n");
        for (int i = 0; i < n; i++) {
            RetrievedChunk chunk = chunks.get(i);
            String title = chunk.getDocTitle();
            if (title == null || title.isBlank()) {
                title = "（历史数据未写入标题，可仅依据文档ID区分）";
            }
            String docId = chunk.getDocId() != null && !chunk.getDocId().isBlank() ? chunk.getDocId() : "—";
            sb.append(String.format("- [%d] 《%s》（文档ID: %s）\n", i + 1, title, docId));
        }
        return sb.toString();
    }

    /**
     * 合并对话历史与 RAG 片段，并做总长度保护。
     */
    public String buildFullContext(String conversationContext, String ragContext) {
        StringBuilder fullContext = new StringBuilder();
        if (conversationContext != null && !conversationContext.isEmpty()) {
            fullContext.append("### 对话历史\n");
            fullContext.append(conversationContext);
            fullContext.append("\n");
        }
        if (ragContext != null && !ragContext.isEmpty()) {
            fullContext.append("### 相关文档\n");
            fullContext.append(ragContext);
        }

        if (fullContext.length() > properties.getMaxContextChars()) {
            return fullContext.substring(0, properties.getMaxContextChars());
        }
        return fullContext.toString();
    }
}
