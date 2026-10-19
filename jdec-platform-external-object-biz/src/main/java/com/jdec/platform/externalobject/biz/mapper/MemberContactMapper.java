package com.jdec.platform.externalobject.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.externalobject.biz.entity.MemberContact;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人员联系方式表（兼具个人通讯录与公司对外联络窗口职责） Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Mapper
public interface MemberContactMapper extends BaseMapper<MemberContact> {}
