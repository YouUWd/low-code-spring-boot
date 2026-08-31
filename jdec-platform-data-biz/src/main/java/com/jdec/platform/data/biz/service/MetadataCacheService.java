package com.jdec.platform.data.biz.service;

import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.shared.context.AppContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 模块元数据缓存服务 跨库调用 config-api 获取 SysModuleCompleteResp 并提供内存高速缓存 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataCacheService {

    private final SysModuleApi sysModuleApi;

    /** 获取指定模块的完整元数据配置 (现阶段直连查询，避免缓存干扰开发联调) */
    public SysModuleCompleteResp getModuleComplete(Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        log.debug(
                "实时加载模块元数据配置: projectNo={}, subjectId={}, moduleId={}",
                projectNo,
                subjectId,
                moduleId);
        return sysModuleApi.getModuleCompleteById(projectNo, subjectId, moduleId);
    }

    /** 清除指定模块的元数据缓存 (占位保留方法签名) */
    public void evict(Long moduleId) {
        // 当前为直查模式，无需清空
    }
}
