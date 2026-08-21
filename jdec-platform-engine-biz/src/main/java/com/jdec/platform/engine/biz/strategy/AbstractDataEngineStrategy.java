package com.jdec.platform.engine.biz.strategy;

import com.jdec.platform.config.api.SysDataPermissionApi;
import com.jdec.platform.config.api.SysModuleFieldPermissionApi;
import com.jdec.platform.config.api.SysModuleQueryApi;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.DataSourceResolver;
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

    @Autowired protected SysModuleQueryApi sysModuleQueryApi;

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
        return sysModuleQueryApi.getModuleCompleteByCode(projectNo, subjectId, moduleCode);
    }

    /** 权限校验逻辑，子类可以覆盖或扩展 */
    protected abstract void checkPermission(
            String projectNo, Long subjectId, SysModuleCompleteResp moduleCompleteResp, T request);

    /** 核心业务逻辑实现，由各个策略子类实现 */
    protected abstract R doExecute(SysModuleCompleteResp moduleCompleteResp, T request);
}
