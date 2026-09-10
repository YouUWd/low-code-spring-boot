package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.response.MoveModuleResp;
import com.jdec.platform.config.api.dto.response.SysModuleListResp;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import com.jdec.platform.config.api.dto.response.SysModuleSimpleTreeResp;
import com.jdec.platform.config.api.dto.response.SysStatusTreeResp;
import java.util.List;

/** 模块管理 API 接口 提供模块完整信息的 CRUD 操作 */
public interface SysModuleApi {

    /**
     * 获取模块列表 返回所有模块的基本信息（不包含树形结构），前端自行构建树
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param category 模块类别 (1=业务模块, 2=系统模块)
     * @return 模块列表
     */
    List<SysModuleListResp> listModules(String projectNo, Long subjectId, Integer category);

    /**
     * 根据模块ID获取模块元数据信息 包含模块基本信息、关联表、字段配置和状态
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param moduleId 模块ID
     * @return 模块元数据响应
     */
    SysModuleMetaResp getModuleCompleteById(String projectNo, Long subjectId, Long moduleId);

    /**
     * 保存或编辑模块完整信息 包含模块基本信息、关联表、字段配置和状态
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param request 保存模块请求
     * @return 模块 ID
     */
    Long saveModule(String projectNo, Long subjectId, SaveModuleReq request);

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
     * @return 移动模块结果
     */
    MoveModuleResp moveModule(MoveModuleReq request);

    /**
     * 查询模块下的状态，组合为一个模块状态树
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param moduleId 模块ID
     * @return 状态树形节点响应列表
     */
    List<SysStatusTreeResp> getModuleStatusTree(String projectNo, Long subjectId, Long moduleId);

    /**
     * 获取所有可用模块的模块树，树中仅包含模块id, moduleCode和moduleName
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @return 简易模块树响应列表
     */
    List<SysModuleSimpleTreeResp> getAvailableModuleTree(
            Integer category, String projectNo, Long subjectId);

    /**
     * 根据字段ID列表批量获取字段元数据
     *
     * @param fieldIds 字段ID列表
     * @return 字段元数据列表
     */
    List<ModuleFieldDTO> listFieldsByIds(List<Long> fieldIds);

    /**
     * 获取指定模块的统一树形表头契约 (遵循第一性原理)
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @param moduleId 模块ID
     * @return 统一树形表头结构
     */
    com.jdec.platform.config.api.dto.response.ModuleHeaderNodeDTO getModuleHeaderTree(
            String projectNo, Long subjectId, Long moduleId);
}
