package com.jdec.platform.common.biz.support.loader;

import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.config.api.SysStatusApi;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/** 模块业务状态信息批量加载器 (供数据行/候选项增强等后置加工复用) */
@Slf4j
@Component
public class StatusInfoLoader {

    private final SysStatusApi sysStatusApi;

    @Autowired
    public StatusInfoLoader(@Autowired(required = false) SysStatusApi sysStatusApi) {
        this.sysStatusApi = sysStatusApi;
    }

    /**
     * 批量加载模块业务状态展示信息
     *
     * @param statusValues 状态值集合
     * @param rootModuleId 根模块 ID
     * @return statusValue -> 状态简要信息
     */
    public Map<Integer, StatusSimpleResp> loadStatusMap(
            Set<Integer> statusValues, Long rootModuleId) {
        Map<Integer, StatusSimpleResp> statusMap = new HashMap<>();
        if (CollectionUtils.isEmpty(statusValues) || rootModuleId == null || sysStatusApi == null) {
            return statusMap;
        }
        for (Integer sv : statusValues) {
            try {
                ModuleApprovalChainConfigResp.StatusNode node =
                        sysStatusApi.getStatusByValue(sv, rootModuleId);
                if (node != null) {
                    statusMap.put(
                            sv,
                            StatusSimpleResp.builder()
                                    .title(node.getTitle())
                                    .statusValue(node.getStatusValue())
                                    .statusBackground(node.getStatusBackground())
                                    .statusFontColor(node.getStatusFontColor())
                                    .build());
                }
            } catch (Exception ex) {
                log.warn(
                        "获取模块状态信息失败: moduleId={}, statusValue={}, err={}",
                        rootModuleId,
                        sv,
                        ex.getMessage());
            }
        }
        return statusMap;
    }

    /** 获取状态信息，未命中时返回仅含 statusValue 的兜底对象 */
    public static StatusSimpleResp orFallback(
            Map<Integer, StatusSimpleResp> statusMap, Integer sv) {
        StatusSimpleResp resp = statusMap.get(sv);
        return resp != null ? resp : StatusSimpleResp.builder().statusValue(sv).build();
    }
}
