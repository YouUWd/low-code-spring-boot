package com.jdec.platform.config.open.api;

import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleNodeDTO;
import com.jdec.platform.config.api.dto.common.ModulePermissionDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
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
     * 获取单个模块的基础元数据 (原子接口，轻量，不拉取字段和关联关系)
     *
     * @param moduleId 模块 ID
     * @return 模块基础节点信息，若不存在返回 null
     */
    ModuleNodeDTO getModuleBasic(Long moduleId);

    /**
     * 获取指定上下文中的模块基础信息，不加载字段、表关系和子孙拓扑。
     *
     * @param projectNo 项目编码 (可选过滤)
     * @param subjectId 主体 ID (可选过滤)
     * @param moduleId 模块 ID
     * @return 模块基础节点信息，若不存在或不属于上下文则返回 null
     */
    ModuleNodeDTO getModuleBasic(String projectNo, Long subjectId, Long moduleId);

    /**
     * 获取指定上下文中的模块基本信息及该模块字段信息，不包含模块树和表关系。
     *
     * @param projectNo 项目编码 (可选过滤)
     * @param subjectId 主体 ID (可选过滤)
     * @param moduleId 模块 ID
     * @return 模块基本信息及字段列表，若不存在或不属于上下文则返回 null
     */
    ModuleInfo getModuleWithFields(String projectNo, Long subjectId, Long moduleId);

    /**
     * 查询指定项目下的所有模块基础节点列表 (用于推导子孙拓扑、白名单与作用域闭包)
     *
     * @param projectNo 项目编码
     * @return 该项目辖下的所有模块基础节点
     */
    List<ModuleNodeDTO> listModulesByProject(String projectNo);

    // ================= 2. 字段字典 (sys_module_field) =================

    /**
     * 根据模块ID获取该模块配置的全部物理字段列表
     *
     * @param moduleId 模块 ID
     * @return 字段列表，按 sortOrder 升序排列
     */
    List<ModuleFieldDTO> listFieldsByModuleId(Long moduleId);

    /**
     * 根据字段ID列表批量获取字段配置 (用于查询计划编译器白名单与平铺字段分流)
     *
     * @param fieldIds 字段 ID 列表
     * @return 字段列表
     */
    List<ModuleFieldDTO> listFieldsByIds(List<Long> fieldIds);

    // ================= 3. 物理表关系 (sys_table_relation) =============

    /**
     * 查询指定项目中涉及指定物理表集合的双向/单向关联关系
     *
     * @param projectNo 项目编码
     * @param tableNames 涉及的物理表名集合
     * @return 关联关系列表
     */
    List<TableRelationDTO> listTableRelations(String projectNo, Collection<String> tableNames);

    // ================= 4. 角色模块字段权限 (sys_role_module_field_permission) =============

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
}
