package com.jdec.platform.shared.service;

import java.util.List;

/**
 * 模块字段查询服务接口
 *
 * <p>提供从权限系统查询模块字段信息的服务
 * <p>通过查询模块权限来获取字段编码列表
 */
public interface ModuleFieldQueryService {

    /**
     * 根据模块编码获取字段编码列表
     *
     * <p>通过查询模块的权限信息，提取字段编码
     * <p>排除模块级权限（fieldCode = "*"）
     *
     * @param moduleCode 模块编码
     * @return 字段编码列表
     */
    List<String> getFieldCodesByModuleCode(String moduleCode);
}
