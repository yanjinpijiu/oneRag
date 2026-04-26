package com.onerag.chat.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onerag.chat.dao.entity.ConversationMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息表 Mapper。
 */
@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessageDO> {
}
