package com.onerag.chat.DTO.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会话列表返回对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRespDTO {
    private String conversationId;
    private String title;
    private Date lastTime;
    private Date createTime;
}
