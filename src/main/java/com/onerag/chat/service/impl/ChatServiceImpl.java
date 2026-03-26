package com.onerag.chat.service.impl;

import com.onerag.chat.DTO.resp.ChatRespDTO;
import com.onerag.chat.service.ChatModelService;
import com.onerag.chat.service.ChatService;
import com.onerag.document.dto.RetrievedChunk;
import com.onerag.document.service.MilvusRetrieverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.onerag.chat.DTO.PromptDTO.PROMPT_TEMPLATE;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final MilvusRetrieverService milvusRetrieverService;
    private final ChatModelService chatModelService;

    @Override
    public ChatRespDTO sendAChat(String message) {
        try {
            log.info("收到用户消息：{}", message);


            //向量检索
            List<RetrievedChunk> retrievedChunks = milvusRetrieverService.retrieve(message);


            if (retrievedChunks.isEmpty()) {
                log.info("未检索到相关文档片段，直接使用大模型回答");
                return chatModelService.chat(message, null);
            }

            String context = buildContext(retrievedChunks);
            log.info("构建的上下文长度：{}，检索到 {} 个文档片段", context.length(), retrievedChunks.size());

            ChatRespDTO response = chatModelService.chat(message, context);

            log.info("成功返回回答，长度：{}", response.getMessage().length());
            return response;

        } catch (Exception e) {
            log.error("处理聊天请求失败", e);
            return new ChatRespDTO("抱歉，处理您的请求时出现错误：" + e.getMessage());
        }
    }

    private String buildContext(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        return chunks.stream()
                .map(chunk -> String.format("[文档片段%d - 相似度:%.2f]\n%s\n",
                        chunk.getChunkIndex(), chunk.getScore(), chunk.getContent()))
                .collect(Collectors.joining("\n"));
    }
}