package com.jdec.platform.approval.biz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jdec.platform.approval.biz.entity.ApprovalVersionRecord;
import com.jdec.platform.approval.biz.mapper.ApprovalVersionRecordMapper;
import com.jdec.platform.approval.biz.service.IApprovalVersionRecordService;
import org.springframework.stereotype.Service;

/**
 * 单据历史内容记录表 服务实现类
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Service
public class ApprovalVersionRecordServiceImpl
        extends ServiceImpl<ApprovalVersionRecordMapper, ApprovalVersionRecord>
        implements IApprovalVersionRecordService {}
