package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.dto.BizLogDTO;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.monitorlog.api.dto.request.MonitorControlLogReq;

public interface BizLog {

    public Boolean referToBizLog(BizLogEnum logEnum);

    public Boolean processBizLog(BizLogEnum logEnum, Object log);

    default MonitorControlLogReq getCommonReq(BizLogDTO bizLogDTO) {
        MonitorControlLogReq req = new MonitorControlLogReq();
        //        req.setLogType(bizLogDTO.getLogType());
        req.setSubjectId(bizLogDTO.getSubjectId());
        req.setLoginUserId(bizLogDTO.getLoginUserId());
        req.setLoginRoleId(bizLogDTO.getRoleId());
        if (bizLogDTO.getMockUserId() != null
                && !bizLogDTO.getLoginUserId().equals(bizLogDTO.getMockUserId())) {
            req.setMockFlag(1);
            req.setMockUserId(bizLogDTO.getMockUserId());
            req.setMockRoleId(bizLogDTO.getRoleId());
        } else {
            req.setMockFlag(0);
        }
        req.setIp(bizLogDTO.getIp());
        req.setBrowser(bizLogDTO.getBrowser());
        req.setPlatForm(bizLogDTO.getPlatform());
        return req;
    }
}
