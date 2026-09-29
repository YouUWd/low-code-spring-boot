package com.jdec.platform.dataengine.biz.dsl;

import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.dataengine.api.dto.model.ModuleFieldDTO;
import com.jdec.platform.dataengine.api.dto.model.TabTreeNodeDTO;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/** jOOQ 多表 Join 组装器 */
@Component
@RequiredArgsConstructor
public class JooqSqlBuilder {

    private final JooqContextFactory jooqContextFactory;

    /** 缓存：表别名 -> Jooq Table对象 */
    private final Map<String, Table<?>> aliasTableMap = new HashMap<>();

    /**
     * 入口方法：根据树形元数据构建带JOIN的查询对象
     *
     * @return SelectFromStep
     */
    private SelectJoinStep<Record> buildJoinSql(SelectSelectStep<Record> selectSelectStep) {
        // score_detail 节点（score的子表）
        TableJoinNode companyNode = new TableJoinNode();
        companyNode.setTableCode("score_detail");
        companyNode.setTableAlias("sd");
        companyNode.setJoinType("LEFT");
        companyNode.setSelfJoinColumn("score_detail.student_score_id");
        companyNode.setParentJoinColumn("score.id");

        // score 节点（student子表）
        TableJoinNode deptNode = new TableJoinNode();
        deptNode.setTableCode("score");
        deptNode.setTableAlias("sc");
        deptNode.setJoinType("LEFT");
        deptNode.setSelfJoinColumn("score.student_id");
        deptNode.setParentJoinColumn("student.id");
        deptNode.getChildren().add(companyNode);

        // class 节点（student子表）
        TableJoinNode classNode = new TableJoinNode();
        classNode.setTableCode("class");
        classNode.setTableAlias("cls");
        classNode.setJoinType("LEFT");
        classNode.setSelfJoinColumn("class.id");
        classNode.setParentJoinColumn("student.class_id");

        // 根节点：student主表
        TableJoinNode root = new TableJoinNode();
        root.setTableCode("student");
        root.setTableAlias("stu");
        // 主表无joinType
        root.getChildren().add(deptNode);
        root.getChildren().add(classNode);

        // 1.初始化主表
        Table<?> mainTable = DSL.table(root.getTableCode());
        aliasTableMap.put(root.getTableAlias(), mainTable);

        SelectJoinStep<Record> selectStep = selectSelectStep.from(DSL.table(root.getTableCode()));

        // 2.递归遍历子节点拼接JOIN
        for (TableJoinNode child : root.getChildren()) {
            selectStep = recursiveJoin(selectStep, child);
        }
        return selectStep;
    }

    /**
     * 递归解析节点，拼接join
     *
     * @param currentStep 当前构建上下文
     * @param node 当前待关联节点
     * @return 拼接后的SelectJoinStep
     */
    private SelectJoinStep<Record> recursiveJoin(
            SelectJoinStep<Record> currentStep, TableJoinNode node) {
        // 创建当前关联表并绑定别名
        Table<?> joinTable = DSL.table(node.getTableCode());
        aliasTableMap.put(node.getTableAlias(), joinTable);

        // 拼接 ON 条件字符串表达式
        Condition onCondition =
                DSL.condition(node.getParentJoinColumn() + " = " + node.getSelfJoinColumn());

        // 判断关联类型
        SelectJoinStep<Record> newStep;
        if ("LEFT".equalsIgnoreCase(node.getJoinType())) {
            newStep = currentStep.leftJoin(joinTable).on(onCondition);
        } else if ("INNER".equalsIgnoreCase(node.getJoinType())) {
            newStep = currentStep.innerJoin(joinTable).on(onCondition);
        } else {
            throw new IllegalArgumentException("不支持的关联类型：" + node.getJoinType());
        }

        // 递归处理当前节点的子表（继续嵌套JOIN）
        for (TableJoinNode subChild : node.getChildren()) {
            newStep = recursiveJoin(newStep, subChild);
        }
        return newStep;
    }

    /** 构建包含主表与 1:1/N:1 关联从表的 SelectJoinStep */
    public SelectJoinStep<Record> buildSelectFrom(
            DSLContext dsl, SysModuleResp completeResp, String tableName) {
        List<Field<?>> fields =
                completeResp.getFields().stream()
                        .filter(ele -> tableName.equals(ele.getTableName()))
                        .map(
                                item ->
                                        DSL.field(
                                                /*item.getTableName() + "." + */ item
                                                        .getColumnName()))
                        .collect(Collectors.toList());

        SelectJoinStep<Record> query = dsl.select(fields).from(tableName);
        return query;
    }

    /** 构建包含主表与 1:1/N:1 关联从表的 SelectJoinStep */
    public SelectJoinStep<Record> buildSelectFrom(StatementContext context) {

        List<Field<?>> fields =
                context.getFields().stream()
                        .map(
                                item ->
                                        DSL.field(
                                                        DSL.name(
                                                                item.getTableName(),
                                                                item.getColumnName()))
                                                .as(
                                                        item.getTableName()
                                                                + "."
                                                                + item.getColumnName()))
                        .collect(Collectors.toList());
        SelectJoinStep<Record> query;
        if (CollectionUtils.isEmpty(fields)) {
            query = context.getDsl().select(DSL.asterisk()).from(context.getTableName());
        } else {
            query = context.getDsl().select(fields).from(context.getTableName());
        }

        Set<String> moduleTables =
                context.getFields().stream()
                        .map(ModuleFieldDTO::getTableName)
                        .collect(Collectors.toSet());
        moduleTables.remove(context.getTableName());

        TabTreeNodeDTO tableTreeNode = TabTreeNodeDTO.buildCompanyRoot();

        for (String tableName : moduleTables) {
            TabTreeNodeDTO targetNode = TabTreeNodeDTO.findSubTreeByValue(tableTreeNode, tableName);
            query.leftJoin(DSL.table(DSL.name(targetNode.getNode().getTableName())))
                    .on(
                            DSL.field(
                                            DSL.name(
                                                    targetNode.getNode().getParentTableName(),
                                                    targetNode.getNode().getParentTableField()))
                                    .eq(
                                            DSL.field(
                                                    DSL.name(
                                                            targetNode.getNode().getTableName(),
                                                            targetNode
                                                                    .getNode()
                                                                    .getSubTableField()))));
        }

        return query;
    }

    /** 表关联树节点：多叉树，一个节点可以拥有多个子关联表 */
    @Data
    private class TableJoinNode {

        /** 当前表编码（对应Jooq生成的表常量） */
        private String tableCode;

        /** 表别名，如 t1、t2 */
        private String tableAlias;

        /** 关联方式：INNER / LEFT */
        private String joinType;

        /** 当前表关联父表的字段：当前表.关联字段 eg: dept.parent_id */
        private String selfJoinColumn;

        /** 父表关联字段：父别名.字段 eg: user.dept_id */
        private String parentJoinColumn;

        /** 子节点列表（多叉树） */
        private List<TableJoinNode> children = new ArrayList<>();
    }
}
