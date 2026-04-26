package com.onerag.chat.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 对话消息表实体。
 * 记录单条消息内容及其角色（user/assistant）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_conversation_message")
public class ConversationMessageDO {

    /** 自增主键（MyBatis-Plus 分配）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属业务对话ID。 */
    private String conversationId;

    /** 用户ID。 */
    private String userId;

    /** 消息角色。 */
    private String role;

    /** 消息正文。 */
    private String content;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;
}
