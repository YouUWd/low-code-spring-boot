package com.jdec.platform.dataengine.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.dataengine.biz.entity.SysModule;
import com.jdec.platform.dataengine.biz.entity.SysModuleField;
import com.jdec.platform.dataengine.biz.mapper.MySysModuleFieldMapper;
import com.jdec.platform.dataengine.biz.mapper.MySysModuleMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

/** 模块元数据缓存服务 跨库调用 config-api 获取 SysModuleResp 并提供内存高速缓存 */
@Slf4j
@Service
@RequiredArgsConstructor
@DataSource("config_engine")
public class MetadataCacheService {

    // private final SysModuleApi sysModuleApi;

    private final MySysModuleMapper mySysModuleMapper;

    private final MySysModuleFieldMapper mySysModuleFieldMapper;

    // 本地缓存: key 为 "projectNo:subjectId:moduleId"
    private final Map<String, SysModuleResp> moduleCache = new ConcurrentHashMap<>();

    /** 获取指定模块的完整元数据配置 */
    public SysModuleResp getModuleComplete(Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        String cacheKey = String.format("%s:%s:%s", projectNo, subjectId, moduleId);

        //        return moduleCache.computeIfAbsent(
        //                cacheKey,
        //                k -> {
        //                    log.info(
        //                            "加载模块元数据配置: projectNo={}, subjectId={}, moduleId={}",
        //                            projectNo,
        //                            subjectId,
        //                            moduleId);
        //                    try {
        //                        DataSourceContextHolder.set("config_engine");
        //                        Set<String> moduleTables = getModuleTables(moduleId);
        //                        return sysModuleApi.getModuleCompleteById(projectNo, subjectId,
        // moduleId);
        //                    } finally {
        //                        DataSourceContextHolder.clear();
        //                    }
        //                });

        LambdaQueryWrapper<SysModule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysModule::getId, moduleId);
        queryWrapper.eq(SysModule::getProjectNo, projectNo);
        queryWrapper.eq(SysModule::getSubjectId, subjectId);
        SysModule module = mySysModuleMapper.selectOne(queryWrapper);
        return buildModuleCompleteResp(module);
    }

    private SysModuleResp buildModuleCompleteResp(SysModule module) {
        SysModuleResp resp = new SysModuleResp();

        // 1. 设置模块基本信息
        SysModuleResp.ModuleInfo moduleInfo = new SysModuleResp.ModuleInfo();
        moduleInfo.setId(module.getId());
        moduleInfo.setProjectNo(module.getProjectNo());
        moduleInfo.setSubjectId(module.getSubjectId());
        //        moduleInfo.setCategory(module.getCategory());
        //        moduleInfo.setSourceSubjects(module.getSourceSubjects());
        moduleInfo.setModuleCode(module.getModuleCode());
        moduleInfo.setModuleName(module.getModuleName());
        moduleInfo.setModuleDesc(module.getModuleDesc());
        moduleInfo.setParentId(module.getParentId() != null ? module.getParentId() : 0L);
        moduleInfo.setPrimaryTable(module.getPrimaryTable());
        moduleInfo.setSortOrder(module.getSortOrder());
        moduleInfo.setCreatedBy(module.getCreatedBy());
        moduleInfo.setCreatedDate(module.getCreatedDate());
        moduleInfo.setUpdatedBy(module.getUpdatedBy());
        moduleInfo.setUpdatedDate(module.getUpdatedDate());
        resp.setModule(moduleInfo);

        Long moduleId = module.getId();

        // 2. 获取模块字段配置 (仅取启用状态)
        List<SysModuleField> allFields =
                mySysModuleFieldMapper.selectList(
                        Wrappers.<SysModuleField>lambdaQuery()
                                .eq(SysModuleField::getModuleId, moduleId)
                                .eq(SysModuleField::getStatus, 1)
                                .orderByAsc(SysModuleField::getSortOrder));

        List<ModuleFieldDTO> fieldInfos =
                allFields.stream()
                        .map(
                                f -> {
                                    ModuleFieldDTO info = new ModuleFieldDTO();
                                    info.setId(f.getId());
                                    info.setTableName(f.getTableName());
                                    info.setColumnName(f.getColumnName());
                                    info.setDisplayName(f.getDisplayName());
                                    // info.setBizKeyOrder(f.getBizKeyOrder());
                                    info.setSortOrder(f.getSortOrder());
                                    info.setStatus(f.getStatus());
                                    return info;
                                })
                        .collect(Collectors.toList());
        resp.setFields(fieldInfos);

        return resp;
    }

    public Set<String> getModuleTables(Long moduleId) {
        LambdaQueryWrapper<SysModuleField> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysModuleField::getModuleId, moduleId);
        queryWrapper.eq(SysModuleField::getStatus, 1);
        List<SysModuleField> ss = mySysModuleFieldMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(ss)) {
            return null;
        }
        return ss.stream().map(x -> x.getTableName()).collect(Collectors.toSet());
    }

    /** 清除指定模块的元数据缓存 */
    public void evict(Long moduleId) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        String cacheKey = String.format("%s:%s:%s", projectNo, subjectId, moduleId);
        moduleCache.remove(cacheKey);
    }
}
