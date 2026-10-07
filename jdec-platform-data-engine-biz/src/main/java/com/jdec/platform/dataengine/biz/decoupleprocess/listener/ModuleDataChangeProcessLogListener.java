package com.jdec.platform.dataengine.biz.decoupleprocess.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jdec.platform.common.api.MonitorLogApi;
import com.jdec.platform.common.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.common.api.dto.log.MonitorMockInfo;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.dataengine.api.dto.model.ModuleChangeDTO;
import com.jdec.platform.dataengine.biz.decoupleprocess.event.ModuleDataChangeEvent;
import com.jdec.platform.dataengine.biz.service.ModuleDataChangeRowConvertService;
import com.jdec.platform.dataengine.biz.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModuleDataChangeProcessLogListener {

    private final ModuleDataChangeRowConvertService moduleDataChangeRowConvertService;

    private final MonitorLogApi monitorLogApi;

    @Async
    @EventListener(ModuleDataChangeEvent.class)
    public void handleApprovalProcessEvent(ModuleDataChangeEvent changeEvent) {
        if (changeEvent != null && changeEvent.getModuleDataPostContext() != null) {
            log.info("处理数据变更日志，actionId：{}", changeEvent.getModuleDataPostContext().getActionId());
        }
        ModuleChangeDTO moduleChangeDTO =
                moduleDataChangeRowConvertService.covertByModuleDataChangeRow(
                        changeEvent.getModuleDataPostContext().getNestedModuleDataDTO(),
                        changeEvent.getModuleDataPostContext().getChangeMap(),
                        changeEvent.getModuleDataPostContext().getModuleCacheMap());

        MonitorLogSaveReq req = new MonitorLogSaveReq();
        req.setType(Integer.valueOf(0));
        req.setSysType("systemLog");
        req.setSubjectId(changeEvent.getModuleDataPostContext().getSubjectId());
        MonitorLoginInfo monitorLoginInfo = new MonitorLoginInfo();
        monitorLoginInfo.setUserId(changeEvent.getModuleDataPostContext().getRealUserId());
        req.setLoginInfo(monitorLoginInfo);
        MonitorMockInfo mockInfo = new MonitorMockInfo();
        mockInfo.setUserId(changeEvent.getModuleDataPostContext().getMockUserId());
        mockInfo.setFlag(
                !changeEvent
                                .getModuleDataPostContext()
                                .getRealUserId()
                                .equals(changeEvent.getModuleDataPostContext().getMockUserId())
                        ? 1
                        : 0);
        req.setMockInfo(mockInfo);
        req.setIp(changeEvent.getModuleDataPostContext().getRequestContextSnapshot().getClientIp());
        req.setBrowser(
                changeEvent.getModuleDataPostContext().getRequestContextSnapshot().getBrowser());
        req.setPlatForm(
                changeEvent.getModuleDataPostContext().getRequestContextSnapshot().getPlatform());
        req.setModuleId(
                changeEvent
                        .getModuleDataPostContext()
                        .getModuleCacheMap()
                        .get(
                                changeEvent
                                        .getModuleDataPostContext()
                                        .getNestedModuleDataDTO()
                                        .getModuleId())
                        .getModuleTree()
                        .getRootModuleId());
        req.setDataId(null);
        req.setContent(null);
        req.setRemark(moduleChangeDTO);
        monitorLogApi.saveLog(req);

        try {
            log.info("处理数据变更日志，moduleChangeDTO：{}", JsonUtil.obj2Json(moduleChangeDTO));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
