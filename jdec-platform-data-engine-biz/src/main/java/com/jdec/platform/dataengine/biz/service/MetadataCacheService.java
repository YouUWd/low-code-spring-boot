package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.shared.context.AppContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 模块元数据缓存服务 跨库调用 config-api 获取 SysModuleResp 并提供内存高速缓存 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataCacheService {

    private final SysModuleApi sysModuleApi;

    // 本地缓存: key 为 "projectNo:subjectId:moduleId"
    private final Map<String, SysModuleResp> moduleCache = new ConcurrentHashMap<>();
    private static final Map<Long, SysModuleResp> moduleCacheById = new ConcurrentHashMap<>();

    static {
        {
            SysModuleResp sysModuleCompleteResp = new SysModuleResp();
            SysModuleResp.ModuleInfo moduleInfo = new SysModuleResp.ModuleInfo();
            moduleInfo.setId(101L);
            moduleInfo.setPrimaryTable("student");

            ModuleTableDTO moduleTableDTO = new ModuleTableDTO();
            moduleTableDTO.setTableName("course");
            sysModuleCompleteResp.setModule(moduleInfo);
            // sysModuleCompleteResp.setModuleTables(List.of((moduleTableDTO)));

            moduleCacheById.put(101L, sysModuleCompleteResp);
        }
        {
            SysModuleResp sysModuleCompleteResp = new SysModuleResp();
            SysModuleResp.ModuleInfo moduleInfo = new SysModuleResp.ModuleInfo();
            moduleInfo.setId(102L);
            moduleInfo.setPrimaryTable("score");

            ModuleTableDTO moduleTableDTO = new ModuleTableDTO();
            moduleTableDTO.setTableName("course");
            sysModuleCompleteResp.setModule(moduleInfo);
            // sysModuleCompleteResp.setModuleTables(null);

            moduleCacheById.put(102L, sysModuleCompleteResp);
        }

        {
            SysModuleResp sysModuleCompleteResp = new SysModuleResp();
            SysModuleResp.ModuleInfo moduleInfo = new SysModuleResp.ModuleInfo();
            moduleInfo.setId(103L);
            moduleInfo.setPrimaryTable("score_detail");

            ModuleTableDTO moduleTableDTO = new ModuleTableDTO();
            moduleTableDTO.setTableName("course");
            sysModuleCompleteResp.setModule(moduleInfo);
            // sysModuleCompleteResp.setModuleTables(null);

            moduleCacheById.put(103L, sysModuleCompleteResp);
        }
    }

    /** 获取指定模块的完整元数据配置 */
    public SysModuleResp getModuleComplete(Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        String cacheKey = String.format("%s:%s:%s", projectNo, subjectId, moduleId);

        return moduleCache.computeIfAbsent(
                cacheKey,
                k -> {
                    log.info(
                            "加载模块元数据配置: projectNo={}, subjectId={}, moduleId={}",
                            projectNo,
                            subjectId,
                            moduleId);
                    return sysModuleApi.getModuleCompleteById(projectNo, subjectId, moduleId);
                });
    }

    /** 清除指定模块的元数据缓存 */
    public void evict(Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        String cacheKey = String.format("%s:%s:%s", projectNo, subjectId, moduleId);
        moduleCache.remove(cacheKey);
    }
}
