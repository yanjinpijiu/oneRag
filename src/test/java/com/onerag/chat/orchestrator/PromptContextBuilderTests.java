package com.onerag.chat.orchestrator;

import com.onerag.chat.config.ChatFlowProperties;
import com.onerag.document.dto.RetrievedChunk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class PromptContextBuilderTests {

    @Test
    void trimChunks_should_limit_by_count_and_chars() {
        ChatFlowProperties properties = new ChatFlowProperties();
        properties.setMaxRagChunks(2);
        properties.setMaxRagContextChars(10);
        PromptContextBuilder builder = new PromptContextBuilder(properties);

        List<RetrievedChunk> input = List.of(
                RetrievedChunk.builder().chunkIndex(1).score(0.9f).content("12345").build(),
                RetrievedChunk.builder().chunkIndex(2).score(0.8f).content("1234").build(),
                RetrievedChunk.builder().chunkIndex(3).score(0.7f).content("1234").build()
        );

        List<RetrievedChunk> trimmed = builder.trimChunks(input);
        Assertions.assertEquals(2, trimmed.size());
    }

    @Test
    void buildRagContext_should_use_sequential_refs_and_bibliography() {
        ChatFlowProperties properties = new ChatFlowProperties();
        PromptContextBuilder builder = new PromptContextBuilder(properties);

        List<RetrievedChunk> input = List.of(
                RetrievedChunk.builder().chunkIndex(0).score(0.9f).content("甲").docTitle("手册A.pdf").docId("d1").build(),
                RetrievedChunk.builder().chunkIndex(0).score(0.8f).content("乙").docTitle("手册B.pdf").docId("d2").build()
        );

        String rag = builder.buildRagContext(input);
        org.junit.jupiter.api.Assertions.assertTrue(rag.contains("[1] 相似度:0.90"));
        org.junit.jupiter.api.Assertions.assertTrue(rag.contains("[2] 相似度:0.80"));
        org.junit.jupiter.api.Assertions.assertTrue(rag.contains("### 参考资料来源"));
        org.junit.jupiter.api.Assertions.assertTrue(rag.contains("- [1] 《手册A.pdf》（文档ID: d1）"));
        org.junit.jupiter.api.Assertions.assertTrue(rag.contains("- [2] 《手册B.pdf》（文档ID: d2）"));
    }

    @Test
    void buildFullContext_should_truncate_to_max_chars() {
        ChatFlowProperties properties = new ChatFlowProperties();
        properties.setMaxContextChars(10);
        PromptContextBuilder builder = new PromptContextBuilder(properties);

        String full = builder.buildFullContext("abcde", "fghij");
        Assertions.assertEquals(10, full.length());
    }
}
