package com.jdec.platform.approval.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.approval.biz.entity.ApprovalChainStatus;
import com.jdec.platform.approval.biz.mapper.ApprovalChainStatusMapper;
import com.jdec.platform.approval.biz.service.IApprovalChainStatusService;
import org.springframework.stereotype.Service;

/**
 * 审批链历史状态表 服务实现类
 *
 * @author JDEC Team
 * @since 2026-07-03
 */
@Service
public class ApprovalChainStatusServiceImpl
        extends ServiceImpl<ApprovalChainStatusMapper, ApprovalChainStatus>
        implements IApprovalChainStatusService {}
