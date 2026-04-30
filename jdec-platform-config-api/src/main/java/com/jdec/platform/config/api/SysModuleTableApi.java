package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.CreateSysModuleTableReq;
import com.jdec.platform.config.api.dto.request.QuerySysModuleTableReq;
import com.jdec.platform.config.api.dto.request.UpdateSysModuleTableReq;
import com.jdec.platform.config.api.dto.response.SysModuleTableDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleTableEntryResp;

import java.util.List;

/**
 * 模块表管理 API 接口
 * 定义模块关联物理表的 CRUD 和查询操作
 */
public interface SysModuleTableApi {

    /**
     * 创建模块表
     *
     * @param request 创建请求
     * @return 模块表条目响应
     */
    SysModuleTableEntryResp createModuleTable(CreateSysModuleTableReq request);

    /**
     * 根据 ID 获取模块表详情
     *
     * @param tableId 表 ID
     * @return 模块表详情响应
     */
    SysModuleTableDetailResp getModuleTableById(Long tableId);

    /**
     * 获取模块下的所有表
     *
     * @param moduleId 模块 ID
     * @return 模块表条目响应列表
     */
    List<SysModuleTableEntryResp> getTablesByModule(Long moduleId);

    /**
     * 查询模块表
     *
     * @param request 查询请求
     * @return 模块表条目响应列表
     */
    List<SysModuleTableEntryResp> queryModuleTables(QuerySysModuleTableReq request);

    /**
     * 更新模块表
     *
     * @param request 更新请求
     * @return 模块表条目响应
     */
    SysModuleTableEntryResp updateModuleTable(UpdateSysModuleTableReq request);

    /**
     * 删除模块表
     *
     * @param tableId 表 ID
     */
    void deleteModuleTable(Long tableId);

    /**
     * 批量删除模块表
     *
     * @param tableIds 表 ID 列表
     */
    void batchDeleteModuleTables(List<Long> tableIds);
}
