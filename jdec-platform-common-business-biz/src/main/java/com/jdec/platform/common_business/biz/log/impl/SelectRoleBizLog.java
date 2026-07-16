package com.jdec.platform.common_business.biz.log.impl;

import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.MonitorLogApiOpenFeignClient;
import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.common_business.biz.dto.BizLogDTO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.dto.configcenter.response.RoleConfigDetailResp;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.common_business.biz.log.BizLog;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class SelectRoleBizLog implements BizLog {

    private final Set<BizLogEnum> referSets = Set.of(BizLogEnum.SELECT_ROLE);

    private final MonitorLogApiOpenFeignClient monitorLogApiOpenFeignClient;

    private final ConfigCenterApiOpenFeignClient configCenterApiOpenFeignClient;

    @Override
    public Boolean referToBizLog(BizLogEnum logEnum) {
        return referSets.contains(logEnum);
    }

    @Override
    public Boolean recordBizLog(BizLogEnum logEnum, Object bizLog) {
        BizLogDTO bizLogDTO = (BizLogDTO) bizLog;
        if (bizLogDTO != null) {
            try {
                TokenContextHolder.setCurrentUserToken(bizLogDTO.getToken());
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
                ConfigApiResponse<RoleConfigDetailResp> roleConfigDetailRespConfigApiResponse =
                        configCenterApiOpenFeignClient.getRoleConfigDetail(
                                bizLogDTO.getCurrentSubjectId(), bizLogDTO.getCurrentRoleId());
                if (ConfigApiResponse.isSuccessResponse(roleConfigDetailRespConfigApiResponse)
                        && roleConfigDetailRespConfigApiResponse.getData() != null) {
                    RoleConfigDetailResp roleConfigDetailResp =
                            roleConfigDetailRespConfigApiResponse.getData();
                    req.setLogContent("切换角色:" + roleConfigDetailResp.getRoleName());
                } else {
                    req.setLogContent("切换角色:" + "");
                }
                monitorLogApiOpenFeignClient.add(req);
                return Boolean.TRUE;
            } catch (Exception e) {
                log.error(
                        "recordBizLog logEnum=" + logEnum + ",log=" + bizLog.toString() + ",e=", e);
            } finally {
                TokenContextHolder.clear();
            }
        }
        return Boolean.FALSE;
    }
}
