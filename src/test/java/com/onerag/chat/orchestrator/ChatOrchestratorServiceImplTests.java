package com.onerag.chat.orchestrator;

import com.onerag.chat.config.ChatFlowProperties;
import com.onerag.chat.orchestrator.impl.ChatOrchestratorServiceImpl;
import com.onerag.chat.service.ConversationService;
import com.onerag.chat.service.IntentRecognitionService;
import com.onerag.chat.service.QueryRewriterService;
import com.onerag.document.dto.RetrievedChunk;
import com.onerag.document.service.BM25RetrieverService;
import com.onerag.document.service.MilvusRetrieverService;
import com.onerag.document.service.RetrievalFusionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatOrchestratorServiceImplTests {

    @Mock
    private MilvusRetrieverService milvusRetrieverService;
    @Mock
    private BM25RetrieverService bm25RetrieverService;
    @Mock
    private RetrievalFusionService retrievalFusionService;
    @Mock
    private QueryRewriterService queryRewriterService;
    @Mock
    private IntentRecognitionService intentRecognitionService;
    @Mock
    private ConversationService conversationService;

    @Test
    void orchestrate_should_skip_retrieval_when_chat_intent() {
        ChatFlowProperties properties = buildDefaultProperties();
        PromptContextBuilder builder = new PromptContextBuilder(properties);
        ChatOrchestratorServiceImpl service = new ChatOrchestratorServiceImpl(
                milvusRetrieverService, bm25RetrieverService, retrievalFusionService,
                queryRewriterService, intentRecognitionService, conversationService, builder, properties);

        when(conversationService.createConversation(anyString(), anyString())).thenReturn("c1");
        when(conversationService.buildContext("c1")).thenReturn("context");
        when(queryRewriterService.rewriteQuery("你好", "context")).thenReturn("你好");
        when(intentRecognitionService.recognizeIntent("你好", "context")).thenReturn("闲聊");

        OrchestrationResult result = service.orchestrate("你好", null);

        Assertions.assertEquals("闲聊", result.getIntent());
        Assertions.assertTrue(result.getRetrievedChunks().isEmpty());
        verify(milvusRetrieverService, never()).retrieve(anyString());
    }

    @Test
    void orchestrate_should_fuse_when_bm25_enabled() {
        ChatFlowProperties properties = buildDefaultProperties();
        properties.setBm25Enabled(true);
        PromptContextBuilder builder = new PromptContextBuilder(properties);
        ChatOrchestratorServiceImpl service = new ChatOrchestratorServiceImpl(
                milvusRetrieverService, bm25RetrieverService, retrievalFusionService,
                queryRewriterService, intentRecognitionService, conversationService, builder, properties);

        RetrievedChunk v = RetrievedChunk.builder().content("v").chunkIndex(1).score(0.8f).build();
        RetrievedChunk b = RetrievedChunk.builder().content("b").chunkIndex(2).score(0.7f).build();
        RetrievedChunk f = RetrievedChunk.builder().content("f").chunkIndex(3).score(0.9f).build();

        when(conversationService.createConversation(anyString(), anyString())).thenReturn("c2");
        when(conversationService.buildContext("c2")).thenReturn("context");
        when(queryRewriterService.rewriteQuery("问题", "context")).thenReturn("问题");
        when(intentRecognitionService.recognizeIntent("问题", "context")).thenReturn("知识问答");
        when(milvusRetrieverService.retrieve("问题")).thenReturn(List.of(v));
        when(bm25RetrieverService.retrieve("问题", properties.getRetrievalTopK())).thenReturn(List.of(b));
        when(retrievalFusionService.fuseResults(List.of(v), List.of(b), properties.getRetrievalTopK())).thenReturn(List.of(f));

        OrchestrationResult result = service.orchestrate("问题", null);

        Assertions.assertEquals(1, result.getRetrievedChunks().size());
        verify(retrievalFusionService).fuseResults(eq(List.of(v)), eq(List.of(b)), anyInt());
    }

    private ChatFlowProperties buildDefaultProperties() {
        ChatFlowProperties properties = new ChatFlowProperties();
        properties.setQueryRewriteEnabled(true);
        properties.setIntentEnabled(true);
        properties.setRetrievalEnabled(true);
        properties.setCacheTtlMillis(60000L);
        properties.setMaxRagChunks(5);
        properties.setMaxRagContextChars(3000);
        properties.setMaxContextChars(4000);
        properties.setRetrievalTopK(5);
        return properties;
    }
}
