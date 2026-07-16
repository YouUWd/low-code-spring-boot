package com.jdec.platform.approval.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.approval.biz.entity.ApprovalChain;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单据审批链记录表 Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Mapper
public interface ApprovalChainMapper extends BaseMapper<ApprovalChain> {}
