package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.Member;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部对象组织与人员树形表（Tab 2 人员构成） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface MemberMapper extends BaseMapper<Member> {}
