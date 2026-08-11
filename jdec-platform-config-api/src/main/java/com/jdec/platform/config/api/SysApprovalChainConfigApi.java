package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.ApprovalChainConfigPageReq;
import com.jdec.platform.config.api.dto.request.ApprovalChainConfigSaveReq;
import com.jdec.platform.config.api.dto.response.*;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;

/** 审批链配置 API */
public interface SysApprovalChainConfigApi {

    /** 获取审批链级联下拉数据（模块 -> 审批链分类） */
    List<ApprovalChainCascadeResp> getCascadeOptions();

    /** 分页查询审批链配置 */
    PageResult<ApprovalChainConfigPageResp> getPage(ApprovalChainConfigPageReq request);

    /** 查询审批链配置详情（返回同模块同类型的所有配置） */
    List<ApprovalChainConfigDetailResp> getDetail(Long id);

    /** 新增或编辑审批链配置 */
    ApprovalChainConfigSaveResp save(ApprovalChainConfigSaveReq request);

    /** 删除审批链配置 */
    void delete(Long id);

    /** 查询审批链配置详情（返回同模块同类型的所有配置） */
    ModuleApprovalChainConfigResp getApprovalConfig(Long moduleId, Long approvalTypeId);

    /** 根据模块ID和角色ID查询包含角色的审批链类型ID列表 */
    List<Long> getApprovalTypeIdList(List<Long> moduleIds, Long roleId);
}
