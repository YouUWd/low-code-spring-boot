package com.jdec.platform.approval.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.approval.biz.entity.ApprovalChainButton;
import com.jdec.platform.approval.biz.mapper.ApprovalChainButtonMapper;
import com.jdec.platform.approval.biz.service.IApprovalChainButtonService;
import org.springframework.stereotype.Service;

/**
 * 单据审批链记录按钮表 服务实现类
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Service
public class ApprovalChainButtonServiceImpl
        extends ServiceImpl<ApprovalChainButtonMapper, ApprovalChainButton>
        implements IApprovalChainButtonService {}
