package com.jdec.platform.approval.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.approval.biz.entity.ApprovalChain;
import com.jdec.platform.approval.biz.mapper.ApprovalChainMapper;
import com.jdec.platform.approval.biz.service.IApprovalChainService;
import org.springframework.stereotype.Service;

/**
 * 单据审批链记录表 服务实现类
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Service
public class ApprovalChainServiceImpl extends ServiceImpl<ApprovalChainMapper, ApprovalChain>
        implements IApprovalChainService {}
