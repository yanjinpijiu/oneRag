package com.onerag.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onerag.user.dao.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
