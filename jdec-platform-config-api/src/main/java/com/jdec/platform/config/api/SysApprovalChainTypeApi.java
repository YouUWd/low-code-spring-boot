package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.ApprovalChainTypePageReq;
import com.jdec.platform.config.api.dto.request.ApprovalChainTypeSaveReq;
import com.jdec.platform.config.api.dto.response.ApprovalChainCascadeResp;
import com.jdec.platform.config.api.dto.response.ApprovalChainTypeOptionResp;
import com.jdec.platform.config.api.dto.response.ApprovalChainTypePageResp;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;

/** 审批链类型 API */
public interface SysApprovalChainTypeApi {

    /** 获取审批链分类级联下拉数据（模块 -> 审批链分类） */
    List<ApprovalChainCascadeResp> getCascadeOptions();

    /** 根据模块id获取审批链分类列表 */
    List<ApprovalChainTypeOptionResp> options(Long moduleId);

    /** 分页查询审批链分类 */
    PageResult<ApprovalChainTypePageResp> getPage(ApprovalChainTypePageReq request);

    /** 新增/编辑审批链分类 */
    void save(ApprovalChainTypeSaveReq request);
}
