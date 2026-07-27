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
        // return referSets.contains(logEnum);
        return false;
    }

    @Override
    public Boolean processBizLog(BizLogEnum logEnum, Object bizLog) {
        BizLogDTO bizLogDTO = (BizLogDTO) bizLog;
        if (bizLogDTO != null) {
            try {
                TokenContextHolder.setCurrentUserToken(bizLogDTO.getToken());
                MonitorLogReq req = getCommonReq(bizLogDTO);
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
                // monitorLogApiOpenFeignClient.add(req);
                return Boolean.TRUE;
            } catch (Exception e) {
                log.error("recordBizLog logEnum={},log={},e=", logEnum, bizLog.toString(), e);
            } finally {
                TokenContextHolder.clear();
            }
        }
        return Boolean.FALSE;
    }
}
