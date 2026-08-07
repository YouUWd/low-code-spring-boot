package com.jdec.platform.approval.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.approval.biz.entity.ApprovalBusiness;
import com.jdec.platform.approval.biz.mapper.ApprovalBusinessMapper;
import com.jdec.platform.approval.biz.service.IApprovalBusinessService;
import org.springframework.stereotype.Service;

/**
 * 通用单据列表 服务实现类
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Service
public class ApprovalBusinessServiceImpl
        extends ServiceImpl<ApprovalBusinessMapper, ApprovalBusiness>
        implements IApprovalBusinessService {}
