package com.jdec.platform.dataengine.biz.third.api.module;

/** 模块业务状态 通用 接口 */
public interface ModuleBizVerificationService {

    /**
     * 用来 检查 模块是否配置审批链
     *
     * @param moduleId
     * @param menuId
     * @return
     */
    public Boolean checkModuleUsingApprovalChain(Long moduleId, Long menuId);
}
