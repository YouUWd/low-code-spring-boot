package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 获取根模块绑定的主表名 */
    public String getPrimaryTable() {
        return rootModule != null ? rootModule.getPrimaryTable() : null;
    }

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

    /** 判断指定物理表名是否存在于当前模块树的任意模块主表中 */
    public boolean containsPhysicalTable(String tableName) {
        if (tableName == null || tableName.isBlank() || moduleMap == null) {
            return false;
        }
        return moduleMap.values().stream()
                .anyMatch(m -> m != null && tableName.equalsIgnoreCase(m.getPrimaryTable()));
    }

    /** 根据物理表名在当前模块树中匹配对应的模块 ID（优先匹配模块主表，其次匹配字段所属表，兜底返回根模块 ID） */
    public Long findModuleIdByTableName(String tableName) {
        String primaryTable = getPrimaryTable();
        if (tableName != null && tableName.equalsIgnoreCase(primaryTable)) {
            return rootModuleId;
        }
        if (tableName != null && moduleMap != null) {
            for (ModuleInfo m : moduleMap.values()) {
                if (m != null && tableName.equalsIgnoreCase(m.getPrimaryTable())) {
                    return m.getModuleId();
                }
            }
            if (fieldMap != null) {
                for (ModuleFieldDTO f : fieldMap.values()) {
                    if (f != null && tableName.equalsIgnoreCase(f.getTableName())) {
                        Long modId = getModuleIdByFieldId(f.getId());
                        if (modId != null) {
                            return modId;
                        }
                    }
                }
            }
        }
        return rootModuleId;
    }

    /** 计算指定模块相对于根模块的层级深度（根模块为 0，不可达返回 Integer.MAX_VALUE） */
    public int getModuleDepth(Long moduleId) {
        if (moduleId == null || rootModuleId == null) {
            return Integer.MAX_VALUE;
        }
        if (moduleId.equals(rootModuleId)) {
            return 0;
        }
        if (moduleMap == null) {
            return Integer.MAX_VALUE;
        }
        int depth = 0;
        Long curr = moduleId;
        Set<Long> visited = new HashSet<>();
        while (curr != null && !curr.equals(rootModuleId)) {
            if (!visited.add(curr)) {
                return Integer.MAX_VALUE;
            }
            ModuleInfo info = moduleMap.get(curr);
            if (info == null || info.getParentId() == null || info.getParentId() <= 0) {
                return Integer.MAX_VALUE;
            }
            curr = info.getParentId();
            depth++;
        }
        return rootModuleId.equals(curr) ? depth : Integer.MAX_VALUE;
    }

    /** 解析指定模块在模块树中归属的顶层 1:N 分支模块 ID（若为根模块或全链路 1:1 则返回 null） */
    public Long resolveTopOneToManyBranch(
            Long moduleId, ProjectTableRelationsDTO projectRelations) {
        if (moduleId == null || rootModuleId == null || moduleId.equals(rootModuleId)) {
            return null;
        }

        List<Long> pathFromChildToRoot = new ArrayList<>();
        Long curr = moduleId;
        Set<Long> visited = new HashSet<>();
        while (!curr.equals(rootModuleId) && visited.add(curr)) {
            pathFromChildToRoot.add(curr);
            ModuleInfo m = getModule(curr);
            if (m == null || m.getParentId() == null || m.getParentId() == 0) {
                break;
            }
            curr = m.getParentId();
        }

        if (pathFromChildToRoot.isEmpty()) {
            return null;
        }

        Collections.reverse(pathFromChildToRoot);

        Long prevModuleId = rootModuleId;
        for (Long stepModuleId : pathFromChildToRoot) {
            ModuleInfo parentMeta = getModule(prevModuleId);
            ModuleInfo childMeta = getModule(stepModuleId);
            String relType = getModuleRelationType(parentMeta, childMeta, projectRelations);
            if ("1:N".equalsIgnoreCase(relType)) {
                return stepModuleId;
            }
            prevModuleId = stepModuleId;
        }
        return null;
    }

    private String getModuleRelationType(
            ModuleInfo parentMeta,
            ModuleInfo childMeta,
            ProjectTableRelationsDTO projectRelations) {
        if (parentMeta == null || childMeta == null) {
            return "1:1";
        }
        String childPrimary = childMeta.getPrimaryTable();
        if (childPrimary == null || childPrimary.isBlank()) {
            return "1:1";
        }

        ModuleInfo physicalParent = parentMeta;
        String parentPrimary = physicalParent.getPrimaryTable();
        Long curP = parentMeta.getParentId();
        Set<Long> visited = new HashSet<>();
        while ((parentPrimary == null || parentPrimary.isBlank())
                && curP != null
                && curP > 0
                && visited.add(curP)) {
            physicalParent = getModule(curP);
            if (physicalParent != null) {
                parentPrimary = physicalParent.getPrimaryTable();
                curP = physicalParent.getParentId();
            } else {
                break;
            }
        }

        if (parentPrimary == null || parentPrimary.isBlank()) {
            return "1:N";
        }
        if (parentPrimary.equalsIgnoreCase(childPrimary)) {
            return "1:1";
        }
        if (projectRelations != null) {
            TableRelationDTO matchedRel =
                    projectRelations.findDirectRelation(parentPrimary, childPrimary);
            if (matchedRel != null
                    && matchedRel.getRelationType() != null
                    && !matchedRel.getRelationType().isBlank()) {
                return matchedRel.getRelationType().trim().toUpperCase();
            }
        }
        return "1:N";
    }
}
