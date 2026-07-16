package com.jdec.platform.approval.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.approval.biz.entity.ApprovalActionLog;
import com.jdec.platform.approval.biz.mapper.ApprovalActionLogMapper;
import com.jdec.platform.approval.biz.service.IApprovalActionLogService;
import org.springframework.stereotype.Service;

/**
 * 单据审批操作日志 服务实现类
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Service
public class ApprovalActionLogServiceImpl
        extends ServiceImpl<ApprovalActionLogMapper, ApprovalActionLog>
        implements IApprovalActionLogService {}
