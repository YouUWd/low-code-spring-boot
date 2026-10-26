package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.SystemInitResp;

/**
 * 系统初始化 API。
 *
 * <p>读取 classpath 下 {@code init/} 目录的初始化 JSON（项目主体、模块、菜单、通用配置、特殊权限），按项目主体逐个初始化，
 * 并为每个主体创建超级管理员角色、绑定全部菜单与特殊权限。
 */
public interface SystemInitApi {

    /**
     * 执行系统初始化。
     *
     * <p>以 {@code init/project_subject.json} 中的项目主体清单为驱动，逐个主体初始化模块、菜单、通用配置、特殊权限，
     * 并创建超级管理员角色、绑定该主体下的全部菜单与特殊权限。已存在的数据按步骤跳过，重复调用不会产生重复数据。
     *
     * @return 各项目主体的初始化明细
     */
    SystemInitResp initSystem();
}
