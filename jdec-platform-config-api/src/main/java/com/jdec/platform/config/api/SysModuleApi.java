package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.api.dto.response.SysModuleDetailResp;
import com.jdec.platform.config.api.dto.response.SysModuleListResp;
import java.util.List;

/** 模块管理 API 接口 提供模块完整信息的 CRUD 操作 */
public interface SysModuleApi {

    /**
     * 获取模块列表 返回所有模块的基本信息（不包含树形结构），前端自行构建树
     *
     * @param projectNo 项目编码（用于租户数据隔离）
     * @return 模块列表
     */
    List<SysModuleListResp> listModules(String projectNo);

    /**
     * 获取模块完整信息 包含模块基本信息、关联表、字段配置和状态
     *
     * @param projectNo 项目编码（用于租户数据隔离）
     * @param moduleCode 模块编码
     * @return 模块完整信息响应
     */
    SysModuleCompleteResp getModuleComplete(String projectNo, String moduleCode);

    /**
     * 保存或编辑模块完整信息 包含模块基本信息、关联表、字段配置和状态
     *
     * <p>如果是新增操作，将自动从 {@code AppContext} 中获取项目编码填充 {@code tenant_id}。
     *
     * @param request 保存模块请求
     * @return 模块详情响应
     */
    SysModuleDetailResp saveModule(SaveModuleReq request);

    /**
     * 删除模块完整信息 删除模块及其所有关联的表、字段配置和状态
     *
     * <p>基于全局唯一 {@code moduleId} 进行操作。
     *
     * @param moduleId 模块 ID
     */
    void deleteModuleComplete(Long moduleId);

    /**
     * 移动模块 更新模块的父模块和排序顺序，用于处理拖拽移动场景
     *
     * <p>基于全局唯一 {@code moduleId} 进行操作。
     *
     * @param request 移动模块请求
     * @return 模块详情响应
     */
    SysModuleDetailResp moveModule(MoveModuleReq request);
}
