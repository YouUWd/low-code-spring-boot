package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.UserFieldConfigReq;
import com.jdec.platform.config.api.dto.response.UserFieldConfigResp;
import java.util.List;

/** 用户字段配置服务接口 */
public interface SysUserFieldConfigApi {

    /** 获取用户自定义的字段配置 */
    List<UserFieldConfigResp> getUserFieldConfigs(Long userId, String moduleCode);

    /** 保存用户自定义的字段配置 */
    void saveUserFieldConfigs(Long userId, String moduleCode, List<UserFieldConfigReq> requests);

    /** 重置用户字段配置为默认 */
    void resetUserFieldConfigs(Long userId, String moduleCode);
}
