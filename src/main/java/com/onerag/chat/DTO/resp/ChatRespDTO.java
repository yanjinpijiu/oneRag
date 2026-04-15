package com.onerag.chat.DTO.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问答接口统一响应体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRespDTO {
    /** 模型最终回复文本。 */
    private String message;
    /** 当前对话ID，前端用于后续续聊。 */
    private String conversationId;
}
