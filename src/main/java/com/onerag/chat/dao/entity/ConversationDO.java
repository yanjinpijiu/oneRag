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
 * 对话主表实体。
 * 用于记录会话级别信息（标题、所属用户、最近活跃时间等）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_conversation")
public class ConversationDO {

    /** 自增主键（MyBatis-Plus 分配）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务对话ID（UUID）。 */
    private String conversationId;

    /** 用户ID。 */
    private String userId;

    /** 对话标题。 */
    private String title;

    /** 最近一次消息时间。 */
    private Date lastTime;

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
