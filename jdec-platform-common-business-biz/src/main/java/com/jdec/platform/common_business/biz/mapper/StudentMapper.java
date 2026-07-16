package com.jdec.platform.common_business.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.common_business.biz.entity.Student;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生表 Mapper 接口
 *
 * @author
 * @since 2026-06-03
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {}
