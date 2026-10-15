package com.jdec.platform.common.biz.service;

import com.jdec.platform.common.api.dto.request.ApprovalReq;
import java.util.List;
import java.util.Map;

/** 审批与流程公共聚合服务接口 */
public interface CommonApprovalService {

    /**
     * 基于菜单 ID 一次性统计待办、已办和全部的全量记录总数
     *
     * @param menuId 菜单 ID
     * @return 映射字典：1 -> 待办数量(todo), 2 -> 已办数量(done), 3 -> 全部数量(total)
     */
    List<Map<String, Object>> getApprovalCountByMenu(Long menuId);

    void approvalBusiness(Long menuId, ApprovalReq req);

    void approvalSave(Long moduleId, ApprovalReq req);

    Map<String, Object> getDetailHead(Long menuId, Long recordId, String btnType);

    Long BusinessNextId(Long menuId);

    List<Map<String, Object>> getApprovalStep(Long menuId, Long id);
}
