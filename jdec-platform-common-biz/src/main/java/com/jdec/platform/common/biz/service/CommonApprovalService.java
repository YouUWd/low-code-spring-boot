package com.jdec.platform.common.biz.service;

import com.jdec.platform.common.api.dto.request.ApprovalReq;
import java.util.List;
import java.util.Map;

/** 审批与流程公共聚合服务接口 */
public interface CommonApprovalService {

    void approvalBusiness(Long menuId, ApprovalReq req);

    void approvalSave(Long moduleId, ApprovalReq req);

    Map<String, Object> getDetailHead(Long menuId, Long recordId, String btnType);

    Long BusinessNextId(Long menuId);

    List<Map<String, Object>> getApprovalStep(Long menuId, Long id);
}
