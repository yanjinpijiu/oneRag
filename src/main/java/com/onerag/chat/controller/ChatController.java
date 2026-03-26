package com.onerag.chat.controller;

import com.onerag.chat.DTO.req.ChatReqDTO;
import com.onerag.chat.DTO.resp.ChatRespDTO;
import com.onerag.chat.service.ChatModelService;
import com.onerag.chat.service.ChatService;
import com.onerag.chat.service.StreamCallback;
import com.onerag.document.dto.RetrievedChunk;
import com.onerag.document.service.MilvusRetrieverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatModelService chatModelService;
    private final MilvusRetrieverService milvusRetrieverService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PostMapping("/send")
    public ResponseEntity<ChatRespDTO> sendAChat(@RequestBody ChatReqDTO chatReqDTO) {
        log.info("收到聊天请求：{}", chatReqDTO.getMessage());
        ChatRespDTO response = chatService.sendAChat(chatReqDTO.getMessage());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatReqDTO chatReqDTO) {
        log.info("收到流式聊天请求：{}", chatReqDTO.getMessage());
        SseEmitter emitter = new SseEmitter(60000L);

        executor.submit(() -> {
            try {
                // 从向量数据库中进行向量检索 （用户发送的语句数据库中的内容）
                List<RetrievedChunk> retrievedChunks = milvusRetrieverService.retrieve(chatReqDTO.getMessage());

                String context = null;
                if (!retrievedChunks.isEmpty()) {
                    context = buildContext(retrievedChunks);
                    log.info("构建的上下文长度：{}，检索到 {} 个文档片段", context.length(), retrievedChunks.size());
                } else {
                    log.info("未检索到相关文档片段，直接使用大模型回答");
                }

                StreamCallback callback = new StreamCallback() {
                    private boolean firstTokenSent = false;

                    @Override
                    public void onFirstToken() {
                        if (!firstTokenSent) {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("first-token")
                                        .data("TTFB: " + System.currentTimeMillis()));
                                firstTokenSent = true;
                                log.info("首包已发送");
                            } catch (Exception e) {
                                log.error("发送首包失败", e);
                            }
                        }
                    }

                    @Override
                    public void onContent(String content) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("content")
                                    .data(content));
                        } catch (Exception e) {
                            log.error("发送内容失败", e);
                        }
                    }

                    @Override
                    public void onComplete() {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data(""));
                            emitter.complete();
                            log.info("流式输出完成");
                        } catch (Exception e) {
                            log.error("发送完成事件失败", e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
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
                chatModelService.streamChat(chatReqDTO.getMessage(), context, callback);

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
            log.info("SSE连接完成");
        });

        emitter.onTimeout(() -> {
            log.warn("SSE连接超时");
            emitter.complete();
        });

        emitter.onError((ex) -> {
            log.error("SSE连接错误", ex);
        });

        return emitter;
    }

    private String buildContext(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            sb.append("文档片段 ").append(i + 1).append(":\n");
            sb.append(chunk.getContent()).append("\n\n");
        }
        return sb.toString();
    }

}
