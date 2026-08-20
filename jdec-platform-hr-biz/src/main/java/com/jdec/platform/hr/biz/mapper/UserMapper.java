package com.jdec.platform.hr.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.hr.biz.entity.User;
import org.apache.ibatis.annotations.Mapper;

/** User Mapper */
@Mapper
public interface UserMapper extends BaseMapper<User> {}
