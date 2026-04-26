package com.onerag.chat.DTO.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会话消息返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageRespDTO {
    private String role;
    private String content;
    private Date createTime;
}
