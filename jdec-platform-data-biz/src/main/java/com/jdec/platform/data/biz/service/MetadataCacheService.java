package com.jdec.platform.data.biz.service;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.shared.context.AppContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 模块元数据缓存服务 自主读取 config_engine 获取 SysModuleMetaResp 并提供内存高速缓存 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataCacheService {

    private final DataModuleConfigService dataModuleConfigService;

    /** 获取指定模块的完整元数据配置 (现阶段直连查询，避免缓存干扰开发联调) */
    public SysModuleMetaResp getModuleComplete(Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        log.debug(
                "实时加载模块元数据配置: projectNo={}, subjectId={}, moduleId={}",
                projectNo,
                subjectId,
                moduleId);
        return dataModuleConfigService.getModuleCompleteById(projectNo, subjectId, moduleId);
    }

    /** 批量获取字段元数据 */
    public List<ModuleFieldDTO> listFieldsByIds(List<Long> fieldIds) {
        return dataModuleConfigService.listFieldsByIds(fieldIds);
    }

    /** 清除指定模块的元数据缓存 (占位保留方法签名) */
    public void evict(Long moduleId) {
        // 当前为直查模式，无需清空
    }
}
