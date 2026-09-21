package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模块子树元数据聚合 DTO (ModuleTreeDTO)
 *
 * <p>包含指定根模块及其所有子孙模块的完整节点信息、字段字典、父子层级映射与白名单闭包， 用于在查询计划编译器等下游核心中实现纯内存零 I/O 校验与树结构推导。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块子树元数据聚合信息")
public class ModuleTreeDTO implements Serializable {

    @Schema(description = "根模块 ID", example = "101")
    private Long rootModuleId;

    @Schema(description = "项目编号", example = "PRJ001")
    private String projectNo;

    @Schema(description = "所属主体 ID", example = "1")
    private Long subjectId;

    @Schema(description = "根模块元数据")
    private ModuleInfo rootModule;

    @Schema(description = "当前子树中所有模块节点元数据映射表 (moduleId -> ModuleInfo)")
    @Builder.Default
    private Map<Long, ModuleInfo> moduleMap = new HashMap<>();

    @Schema(description = "当前子树中的父子关系映射表 (parentId -> List<childModuleId>)")
    @Builder.Default
    private Map<Long, List<Long>> parentToChildren = new HashMap<>();

    @Schema(description = "当前子树中所有物理字段字典映射表 (fieldId -> ModuleFieldDTO)")
    @Builder.Default
    private Map<Long, ModuleFieldDTO> fieldMap = new HashMap<>();

    @Schema(description = "当前子树中字段与模块的归属映射表 (fieldId -> moduleId)")
    @Builder.Default
    private Map<Long, Long> fieldToModuleMap = new HashMap<>();

    @Schema(description = "当前子树包含的合法模块 ID 闭包 (根模块 + 所有子孙模块)")
    @Builder.Default
    private Set<Long> allowedModuleIds = new HashSet<>();

    /** 判断指定模块 ID 是否属于当前模块树闭包 */
    public boolean containsModule(Long moduleId) {
        return moduleId != null && allowedModuleIds != null && allowedModuleIds.contains(moduleId);
    }

    /** 获取指定模块的元数据 */
    public ModuleInfo getModule(Long moduleId) {
        return moduleMap != null ? moduleMap.get(moduleId) : null;
    }

    /** 获取指定字段的元数据 */
    public ModuleFieldDTO getField(Long fieldId) {
        return fieldMap != null ? fieldMap.get(fieldId) : null;
    }

    /** 根据字段 ID 获取所属模块 ID */
    public Long getModuleIdByFieldId(Long fieldId) {
        return fieldToModuleMap != null ? fieldToModuleMap.get(fieldId) : null;
    }

    /** 获取指定父模块在当前子树下的直属子模块 ID 列表 */
    public List<Long> getChildModuleIds(Long parentId) {
        if (parentToChildren == null || parentId == null) {
            return Collections.emptyList();
        }
        return parentToChildren.getOrDefault(parentId, Collections.emptyList());
    }
}
