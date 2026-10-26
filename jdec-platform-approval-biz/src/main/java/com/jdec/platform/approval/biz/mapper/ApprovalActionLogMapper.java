package com.jdec.platform.approval.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.approval.biz.entity.ApprovalActionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单据审批操作日志 Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Mapper
public interface ApprovalActionLogMapper extends BaseMapper<ApprovalActionLog> {}
