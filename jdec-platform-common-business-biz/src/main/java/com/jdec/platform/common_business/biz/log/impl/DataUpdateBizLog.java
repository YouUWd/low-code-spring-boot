package com.jdec.platform.common_business.biz.log.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common_business.api.dto.response.MenuListResp;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.ConfigCenterApiOpenFeignClient;
import com.jdec.platform.common_business.biz.configcenter.client.openfeign.MonitorLogApiOpenFeignClient;
import com.jdec.platform.common_business.biz.context.TokenContextHolder;
import com.jdec.platform.common_business.biz.dto.BizLogDTO;
import com.jdec.platform.common_business.biz.dto.configcenter.response.ConfigApiResponse;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.common_business.biz.log.BizLog;
import com.jdec.platform.monitorlog.api.dto.request.MonitorLogReq;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class DataUpdateBizLog implements BizLog {
    private final Set<BizLogEnum> referSets = Set.of(BizLogEnum.DATA_SAVE, BizLogEnum.DATA_UPDATE);

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
                MonitorLogReq req = getCommonReq(bizLogDTO);
                String menuTitle = "";
                ConfigApiResponse<List<MenuListResp>> resp =
                        configCenterApiOpenFeignClient.getMenuList(
                                bizLogDTO.getCurrentSubjectId(), Integer.valueOf("1"));
                if (ConfigApiResponse.isSuccessResponse(resp)
                        && CollectionUtil.isNotEmpty(resp.getData())) {
                    MenuListResp menu =
                            resp.getData().stream()
                                    .filter(x -> x.getId().equals(bizLogDTO.getMenuId()))
                                    .findFirst()
                                    .orElse(null);
                    if (menu != null) {
                        menuTitle = menu.getTitle();
                    }
                }
                if (logEnum == BizLogEnum.DATA_SAVE) {
                    req.setLogContent("[" + menuTitle + "]-保存数据");
                } else {
                    req.setLogContent("[" + menuTitle + "]-修改数据");
                }
                req.setLogRemark(bizLogDTO.getLogRemark());
                monitorLogApiOpenFeignClient.add(req);
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
