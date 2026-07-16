package com.jdec.platform.approval.biz.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jdec.platform.approval.biz.entity.ApprovalVersionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单据历史内容记录表 Mapper 接口
 *
 * @author JDEC Team
 * @since 2026-06-12
 */
@Mapper
public interface ApprovalVersionRecordMapper extends BaseMapper<ApprovalVersionRecord> {}
