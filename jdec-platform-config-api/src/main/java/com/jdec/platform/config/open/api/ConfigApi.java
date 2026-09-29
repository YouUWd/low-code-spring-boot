package com.jdec.platform.config.open.api;

import com.jdec.platform.config.api.dto.common.FieldWordCloudDTO;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 统一配置开放接口 (供数据引擎、流程引擎等下游核心消费)
 *
 * <p>提供原子化的模块基础信息、字段配置、表拓扑查询能力，以及内存组合的高层门面。
 */
public interface ConfigApi {

    // ================= 1. 模块基础与拓扑 (sys_module) =================

    /**
     * 获取指定根模块及其所有子孙模块的完整聚合树 (包含节点元数据、字段字典、子树父子关系与白名单闭包，基于统一整树缓存)
     *
     * @param projectNo 项目编码 (可选校验)
     * @param subjectId 主体 ID (可选校验)
     * @param rootModuleId 根模块 ID
     * @return 模块子树聚合 DTO，若不存在则返回 null
     */
    ModuleTreeDTO getModuleTree(String projectNo, Long subjectId, Long rootModuleId);

    // ================= 2. 物理表关系 (sys_table_relation) =============

    /**
     * 查询指定项目下的全量物理表关联拓扑聚合信息 (含正向/反向双向索引 Map)
     *
     * @param projectNo 项目编码
     * @return 项目表关联拓扑聚合 DTO，含双向映射 Map
     */
    ProjectTableRelationsDTO getProjectTableRelations(String projectNo);

    // ================= 3. 角色模块字段权限 (sys_role_module_field_permission) =============

    /**
     * 获取指定角色在指定模块列表下的权限汇总信息
     *
     * <p>规则：只要当前模块及其子模块的 sys_role_module_field_permission 中有 view/apply/edit，就代表有相关权限； 且返回对象的
     * fieldPermissions 中涵盖当前模块及其子孙模块的所有字段权限明细。
     *
     * @param roleId 角色 ID
     * @param moduleIds 模块 ID 集合
     * @return 按 moduleId 映射的模块权限 DTO (Map<moduleId, ModulePermissionDTO>)
     */
    Map<Long, ModulePermissionDTO> getModulePermissions(Long roleId, Collection<Long> moduleIds);

    // ================= 4. 字段词云配置 (sys_field) =============

    /**
     * 查询项目及主体下配置了词云关联关系的字段列表
     *
     * @param projectNo 项目编码
     * @param subjectId 主体 ID
     * @return 字段词云配置列表
     */
    List<FieldWordCloudDTO> getWordCloudFields(String projectNo, Long subjectId);

    // ================= 5. 元数据缓存管理 (Cache Eviction) =============

    /**
     * 精准清理指定模块的元数据缓存 (包括模块基础信息、字段配置、子孙拓扑闭包等)
     *
     * @param moduleId 模块 ID
     */
    void clearModuleCache(Long moduleId);

    /** 全量清空所有配置元数据缓存 (模块、字段、表关系、子孙拓扑等) */
    void clearMetaCache();
}
