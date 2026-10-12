package com.jdec.platform.data.biz.plan.compiler;

import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.api.dto.common.ProjectTableRelationsDTO;
import com.jdec.platform.config.api.dto.common.TableRelationDTO;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 模块物理主表与表间关联关系的解析工具 (ModuleRelationResolver)
 *
 * <p>读侧 {@link QueryPlanCompiler} 与写侧 {@link SavePlanCompiler} 必须共享同一套口径：主表只认模块显式声明的 {@code
 * primary_table}，虚拟父模块一律沿模块树向上回溯到最近的物理祖先，父子同表视为 1:1 垂直拆分。 把这些判定收敛到一处，避免两侧各自实现后逐渐漂移，导致「读得出、写不进」。
 */
public final class ModuleRelationResolver {

    private ModuleRelationResolver() {}

    /** 父子模块间的关联关系：父表关联键、子表外键、父表主表名、关系类型。 */
    public record ParentRelation(
            String parentKey,
            String childForeignKey,
            String parentPrimaryTable,
            String relationType) {}

    /**
     * 解析模块的物理主表名：只认模块显式声明的 {@code primary_table}。
     *
     * <p>刻意不做「回退取首个字段表名」：会让声明了字段的虚拟模块静默变成物理模块，必须与保存侧同口径。
     *
     * @return 去空格后的表名；未声明时返回空串（非 {@code null}）
     */
    public static String resolvePrimaryTable(ModuleInfo meta) {
        if (meta == null || meta.getPrimaryTable() == null || meta.getPrimaryTable().isBlank()) {
            return "";
        }
        return meta.getPrimaryTable().trim();
    }

    /** 沿模块树向上回溯最近的一个配置了有效物理主表的父/祖先模块，同时把沿途的中间虚拟模块收集进 {@code intermediateCollector}。 */
    public static ModuleInfo findNearestPhysicalParentMeta(
            ModuleInfo currentMeta, List<ModuleInfo> intermediateCollector, ModuleTreeDTO tree) {
        if (currentMeta == null) {
            return null;
        }
        Long curParentId = currentMeta.getParentId();
        Set<Long> visited = new HashSet<>();
        while (curParentId != null && curParentId > 0 && visited.add(curParentId)) {
            ModuleInfo pMeta = tree != null ? tree.getModule(curParentId) : null;
            if (pMeta == null) {
                break;
            }
            String pTable = resolvePrimaryTable(pMeta);
            if (pTable != null && !pTable.isBlank()) {
                return pMeta;
            }
            if (intermediateCollector != null) {
                intermediateCollector.add(pMeta);
            }
            curParentId = pMeta.getParentId();
        }
        return null;
    }

    /**
     * 解析单向定义的 {@code sys_table_relation} 关联（main_table 为父表，join_table 为从表）。
     *
     * <p>父子模块落在同一物理表时（垂直拆分视图），外键即自身主键 {@code id}，直接合成 1:1 关系。
     *
     * @return 匹配到的关系；无物理父表、父子同表以外的未配置关系时返回 {@code null}
     */
    public static TableRelationDTO resolveDirectRelation(
            String parentPrimary, String childPrimaryTable, ProjectTableRelationsDTO relations) {
        if (parentPrimary == null
                || parentPrimary.isBlank()
                || childPrimaryTable == null
                || childPrimaryTable.isBlank()) {
            return null;
        }
        if (parentPrimary.equalsIgnoreCase(childPrimaryTable)) {
            return TableRelationDTO.builder()
                    .mainTable(parentPrimary)
                    .joinTable(childPrimaryTable)
                    .joinField("id")
                    .relationType("1:1")
                    .build();
        }
        return relations != null
                ? relations.findDirectRelation(parentPrimary, childPrimaryTable)
                : null;
    }

    /** 取关系类型，缺省回退到给定值。 */
    public static String relationType(TableRelationDTO relation, String fallback) {
        return (relation != null
                        && relation.getRelationType() != null
                        && !relation.getRelationType().isBlank())
                ? relation.getRelationType().trim()
                : fallback;
    }

    /**
     * 判断物理表是否已被模块树「认领」——即它是树中某个模块的 {@code primary_table}。
     *
     * <p>被认领的表由模块树按独立子节点取数，不能再当作伴生表 JOIN 进父行，否则同一份数据会在报文里出现两次。
     */
    public static boolean isModulePrimaryTable(String table, ModuleTreeDTO tree) {
        if (table == null || table.isBlank() || tree == null || tree.getModuleMap() == null) {
            return false;
        }
        String target = table.trim();
        for (ModuleInfo m : tree.getModuleMap().values()) {
            String pt = resolvePrimaryTable(m);
            if (!pt.isEmpty() && pt.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断一条 {@code sys_table_relation} 是否构成「伴生表」（垂直拆分）关系。
     *
     * <p>元数据表里 {@code relation_type} 只有 {@code '1:1'} 与 {@code '1:N'} 两种取值（真实库实测 20 行全部如此），
     * 因此「伴生」不能仅靠关系类型判定，必须叠加一条判据：<b>join_table 未被模块树认领</b>。
     *
     * <p>两个真实例子说明为何缺一不可：company 的 {@code info -> basic_info/address/credit_rating} 同为 1:1，
     * 但三张从表各自都是模块主表（1002/1005/1010），已由模块树取数；而 school 的 {@code course -> course_syllabus}、 {@code
     * student -> student_profile} 的从表不是任何模块的主表，只出现在所属模块的字段字典里，才是真正需要 LEFT JOIN 进同一行的垂直拆分。
     */
    public static boolean isCompanionRelation(TableRelationDTO relation, ModuleTreeDTO tree) {
        if (relation == null || !"1:1".equalsIgnoreCase(relationType(relation, ""))) {
            return false;
        }
        return !isModulePrimaryTable(relation.getJoinTable(), tree);
    }

    /**
     * 判断一条 {@code sys_table_relation} 是否构成当前表的「上游」（多对一）关系。
     *
     * <p>{@code sys_table_relation} 是单向记录的：{@code main_table} 是「一」的一方。当一条关系的 {@code join_table}
     * 恰是当前表时，当前表是「多」的一方 —— 例如 school 的 {@code teacher -> course} 表示 {@code course.teacher_id =
     * teacher.id}，从 course 的视角看每个课程最多对应一位教师， 因此 LEFT JOIN 上游表**不会放大行数**，是安全的。
     *
     * <p>与 {@link #isCompanionRelation} 对称地要求 {@code main_table} 未被模块树认领：若上游表本身就是某个模块的 {@code
     * primary_table}，那它与当前模块的父子关系已由模块树建模，不该再 JOIN 进来。
     */
    public static boolean isUpstreamRelation(
            TableRelationDTO relation, String currentTable, ModuleTreeDTO tree) {
        if (relation == null || currentTable == null || currentTable.isBlank()) {
            return false;
        }
        if (relation.getJoinTable() == null
                || !relation.getJoinTable().trim().equalsIgnoreCase(currentTable.trim())) {
            return false;
        }
        if (relation.getMainTable() == null || relation.getMainTable().isBlank()) {
            return false;
        }
        // 关联键缺一不可，否则拼不出 ON 条件
        if (relation.getMainField() == null
                || relation.getMainField().isBlank()
                || relation.getJoinField() == null
                || relation.getJoinField().isBlank()) {
            return false;
        }
        return !isModulePrimaryTable(relation.getMainTable(), tree);
    }
}
