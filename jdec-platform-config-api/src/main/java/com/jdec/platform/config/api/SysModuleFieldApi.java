package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.CreateSysModuleFieldReq;
import com.jdec.platform.config.api.dto.request.QuerySysModuleFieldReq;
import com.jdec.platform.config.api.dto.request.UpdateSysModuleFieldReq;
import com.jdec.platform.config.api.dto.response.SysModuleFieldDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleFieldEntryResp;

import java.util.List;

/**
 * 模块字段管理 API 接口
 * 定义模块字段配置的 CRUD 和查询操作
 */
public interface SysModuleFieldApi {

    /**
     * 创建模块字段
     *
     * @param request 创建请求
     * @return 模块字段条目响应
     */
    SysModuleFieldEntryResp createModuleField(CreateSysModuleFieldReq request);

    /**
     * 根据 ID 获取模块字段详情
     *
     * @param fieldId 字段 ID
     * @return 模块字段详情响应
     */
    SysModuleFieldDetailResp getModuleFieldById(Long fieldId);

    /**
     * 获取模块下的所有字段
     *
     * @param moduleId 模块 ID
     * @return 模块字段条目响应列表
     */
    List<SysModuleFieldEntryResp> getFieldsByModule(Long moduleId);

    /**
     * 查询模块字段
     *
     * @param request 查询请求
     * @return 模块字段条目响应列表
     */
    List<SysModuleFieldEntryResp> queryModuleFields(QuerySysModuleFieldReq request);

    /**
     * 更新模块字段
     *
     * @param request 更新请求
     * @return 模块字段条目响应
     */
    SysModuleFieldEntryResp updateModuleField(UpdateSysModuleFieldReq request);

    /**
     * 删除模块字段
     *
     * @param fieldId 字段 ID
     */
    void deleteModuleField(Long fieldId);

    /**
     * 批量删除模块字段
     *
     * @param fieldIds 字段 ID 列表
     */
    void batchDeleteModuleFields(List<Long> fieldIds);
}
