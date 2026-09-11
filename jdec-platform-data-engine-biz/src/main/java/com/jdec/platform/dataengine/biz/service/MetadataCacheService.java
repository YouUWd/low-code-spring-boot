package com.jdec.platform.dataengine.biz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.config.biz.entity.SysModuleField;
import com.jdec.platform.config.biz.mapper.SysModuleFieldMapper;
import com.jdec.platform.shared.context.AppContext;
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
public class MetadataCacheService {

    private final SysModuleApi sysModuleApi;

    private final SysModuleFieldMapper sysModuleFieldMapper;

    // 本地缓存: key 为 "projectNo:subjectId:moduleId"
    private final Map<String, SysModuleResp> moduleCache = new ConcurrentHashMap<>();

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

    public Set<String> getModuleTables(Long moduleId) {
        LambdaQueryWrapper<SysModuleField> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysModuleField::getModuleId, moduleId);
        List<SysModuleField> ss = sysModuleFieldMapper.selectList(queryWrapper);
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
