package com.jdec.platform.approval.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.approval.biz.entity.ApprovalChainStatus;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批链历史状态表 Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-07-03
 */
@Mapper
public interface ApprovalChainStatusMapper extends BaseMapper<ApprovalChainStatus> {}
