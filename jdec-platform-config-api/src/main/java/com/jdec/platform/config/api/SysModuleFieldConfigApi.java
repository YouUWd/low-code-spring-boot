package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.ModuleFieldConfigReq;
import com.jdec.platform.config.api.dto.response.ModuleFieldConfigResp;
import java.util.List;

/** 模块字段配置服务接口 */
public interface SysModuleFieldConfigApi {

    /** 获取模块的字段配置 */
    List<ModuleFieldConfigResp> getModuleFieldConfigs(String moduleCode);

    /** 添加字段配置 */
    ModuleFieldConfigResp addFieldConfig(String moduleCode, ModuleFieldConfigReq request);

    /** 更新字段配置 */
    ModuleFieldConfigResp updateFieldConfig(
            String moduleCode, String fieldCode, ModuleFieldConfigReq request);

    /** 删除字段配置 */
    void deleteFieldConfig(String moduleCode, String fieldCode);

    /** 批量更新字段配置 */
    List<ModuleFieldConfigResp> updateFieldConfigs(
            String moduleCode, List<ModuleFieldConfigReq> requests);
}
