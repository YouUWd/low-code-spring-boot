package com.jdec.platform.config.biz.engine.strategy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 数据引擎策略抽象类 (模板方法模式)
 *
 * @param <T> 请求参数类型
 * @param <R> 返回结果类型
 */
@Slf4j
public abstract class AbstractDataEngineStrategy<T, R> {

    @Autowired protected SysModuleApi sysModuleApi;

    @Autowired protected SysModuleMapper sysModuleMapper;

    @Autowired protected SysDataPermissionApi sysDataPermissionApi;

    @Autowired protected SysModuleFieldPermissionApi sysModuleFieldPermissionApi;

    @Autowired protected DataSourceResolver dataSourceResolver;

    /** 模板方法：定义了数据引擎操作的标准流程 */
    public R execute(String moduleCode, T request) {
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();

        // 1. 获取模块配置信息
        SysModuleCompleteResp moduleCompleteResp =
                getModuleCompleteInfo(projectNo, subjectId, moduleCode);

        // 2. 权限校验准备
        checkPermission(projectNo, subjectId, moduleCompleteResp, request);

        // 3. 执行核心数据操作
        return doExecute(moduleCompleteResp, request);
    }

    private SysModuleCompleteResp getModuleCompleteInfo(
            String projectNo, Long subjectId, String moduleCode) {
        // 根据 moduleCode 获取模块 ID
        SysModule module =
                sysModuleMapper.selectOne(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(SysModule::getSubjectId, subjectId)
                                .eq(SysModule::getModuleCode, moduleCode));

        if (module == null) {
            throw new BusinessException("模块不存在: " + moduleCode);
        }

        return sysModuleApi.getModuleCompleteById(projectNo, subjectId, module.getId());
    }

    /** 权限校验逻辑，子类可以覆盖或扩展 */
    protected abstract void checkPermission(
            String projectNo, Long subjectId, SysModuleCompleteResp moduleCompleteResp, T request);

    /** 核心业务逻辑实现，由各个策略子类实现 */
    protected abstract R doExecute(SysModuleCompleteResp moduleCompleteResp, T request);
}
