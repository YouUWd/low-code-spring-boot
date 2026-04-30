package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.CreateSysModuleReq;
import com.jdec.platform.config.api.dto.request.QuerySysModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.request.SortOrderUpdateReq;
import com.jdec.platform.config.api.dto.request.UpdateSysModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleEntryResp;
import com.jdec.platform.config.api.dto.response.SysModuleTreeResp;

import java.util.List;

/**
 * 模块管理 API 接口
 * 定义模块的 CRUD 和查询操作
 */
public interface SysModuleApi {

    /**
     * 创建模块
     *
     * @param request 创建模块请求
     * @return 模块条目响应
     */
    SysModuleEntryResp createModule(CreateSysModuleReq request);

    /**
     * 根据 ID 获取模块详情
     *
     * @param moduleId 模块 ID
     * @return 模块详情响应
     */
    SysModuleDetailResp getModuleById(Long moduleId);

    /**
     * 获取所有模块（树形结构）
     *
     * @param tenantId 租户 ID
     * @return 模块树形响应列表
     */
    List<SysModuleTreeResp> getModuleTree(Long tenantId);

    /**
     * 查询模块列表
     *
     * @param request 查询请求
     * @return 模块条目响应列表
     */
    List<SysModuleEntryResp> queryModules(QuerySysModuleReq request);

    /**
     * 更新模块
     *
     * @param request 更新模块请求
     * @return 模块条目响应
     */
    SysModuleEntryResp updateModule(UpdateSysModuleReq request);

    /**
     * 删除模块
     *
     * @param moduleId 模块 ID
     */
    void deleteModule(Long moduleId);

    /**
     * 批量删除模块
     *
     * @param moduleIds 模块 ID 列表
     */
    void batchDeleteModules(List<Long> moduleIds);

    /**
     * 批量更新模块排序
     * 用于前端拖拽排序场景
     *
     * @param requests 排序更新请求列表
     */
    void batchUpdateSortOrder(List<SortOrderUpdateReq> requests);

    /**
     * 保存模块完整信息
     * 包含模块基本信息、关联表、字段配置和状态
     *
     * @param request 保存模块请求
     * @return 模块详情响应
     */
    SysModuleDetailResp saveModule(SaveModuleReq request);
}
