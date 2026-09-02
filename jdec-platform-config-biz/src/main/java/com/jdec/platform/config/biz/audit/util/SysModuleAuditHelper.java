package com.jdec.platform.config.biz.audit.util;

import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 模块配置变更审计日志帮助类（轻量版） */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysModuleAuditHelper {

    /** 转换工具：将数据库查询出的 SysModuleMetaResp 转为标准的 SaveModuleReq */
    public SaveModuleReq convertRespToReq(SysModuleMetaResp resp) {
        if (resp == null) {
            return null;
        }
        SaveModuleReq req = new SaveModuleReq();
        if (resp.getModule() != null) {
            SaveModuleReq.SaveSysModuleReq moduleReq = new SaveModuleReq.SaveSysModuleReq();
            moduleReq.setId(resp.getModule().getId());
            moduleReq.setModuleCode(resp.getModule().getModuleCode());
            moduleReq.setModuleName(resp.getModule().getModuleName());
            moduleReq.setModuleDesc(resp.getModule().getModuleDesc());
            moduleReq.setSortOrder(resp.getModule().getSortOrder());
            req.setModule(moduleReq);
        }
        req.setFields(resp.getFields());
        req.setModuleHeaders(resp.getModuleHeaders());
        req.setModuleStatuses(resp.getModuleStatuses());
        return req;
    }

    public String buildSaveModuleRemark(
            String projectNo, Long subjectId, SaveModuleReq oldReq, SaveModuleReq newReq) {
        String moduleName =
                newReq != null && newReq.getModule() != null
                        ? newReq.getModule().getModuleName()
                        : "";
        return oldReq == null ? "新增模块: " + moduleName : "更新模块: " + moduleName;
    }

    public String buildDeleteModuleRemark(SysModuleMetaResp moduleData) {
        String moduleName =
                moduleData != null && moduleData.getModule() != null
                        ? moduleData.getModule().getModuleName()
                        : "";
        return "删除模块: " + moduleName;
    }

    public String buildMoveModuleRemark(SysModuleMetaResp fromModule, SysModuleMetaResp toModule) {
        String fromName =
                fromModule != null && fromModule.getModule() != null
                        ? fromModule.getModule().getModuleName()
                        : "";
        String toName =
                toModule != null && toModule.getModule() != null
                        ? toModule.getModule().getModuleName()
                        : "";
        return "移动模块: 从 [" + fromName + "] 移动到 [" + toName + "]";
    }
}
