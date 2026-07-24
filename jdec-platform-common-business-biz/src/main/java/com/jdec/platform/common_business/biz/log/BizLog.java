package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.dto.BizLogDTO;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;

public interface BizLog {

    public Boolean referToBizLog(BizLogEnum logEnum);

    public Boolean recordBizLog(BizLogEnum logEnum, Object log);

    default MonitorLogReq getCommonReq(BizLogDTO bizLogDTO) {
        MonitorLogReq req = new MonitorLogReq();
        req.setLogType(bizLogDTO.getLogType());
        req.setSubjectId(bizLogDTO.getCurrentSubjectId());
        req.setLoginUserId(bizLogDTO.getLoginUserId());
        req.setLoginRoleId(bizLogDTO.getCurrentRoleId());
        if (bizLogDTO.getMockUserId() != null
                && !bizLogDTO.getLoginUserId().equals(bizLogDTO.getMockUserId())) {
            req.setMockFlag(1);
            req.setMockUserId(bizLogDTO.getMockUserId());
            req.setMockRoleId(bizLogDTO.getCurrentRoleId());
        } else {
            req.setMockFlag(0);
        }
        req.setIp(bizLogDTO.getIp());
        req.setBrowser(bizLogDTO.getBrowser());
        req.setPlatForm(bizLogDTO.getPlatform());
        return req;
    }
}
