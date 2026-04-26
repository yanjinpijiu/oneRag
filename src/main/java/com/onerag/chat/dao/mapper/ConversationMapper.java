package com.onerag.chat.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onerag.chat.dao.entity.ConversationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话主表 Mapper。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationDO> {
}
