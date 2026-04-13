package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.RegisterModuleReq;
import com.jdec.platform.config.api.dto.request.UpdateModuleReq;
import com.jdec.platform.config.api.dto.response.ModuleEntryResp;
import java.util.List;

/** 系统模块服务接口 */
public interface SysModuleApi {

    // ==================== 模块管理 ====================

    /** 获取所有模块 */
    List<ModuleEntryResp> getModuleList();

    /** 根据 ID 获取模块 */
    ModuleEntryResp getModuleById(Long moduleId);

    /** 根据模块编码获取模块 */
    ModuleEntryResp getModuleByCode(String moduleCode);

    /** 注册新模块 */
    ModuleEntryResp registerModule(RegisterModuleReq request);

    /** 更新模块 */
    ModuleEntryResp updateModule(Long moduleId, UpdateModuleReq request);

    /** 删除模块 */
    void deleteModule(Long moduleId);
}
