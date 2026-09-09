package com.jdec.platform.data.biz.plan.model;

import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jooq.Condition;
import org.jooq.OrderField;

/**
 * 单个模块执行单元编译后的查询执行计划
 *
 * <p>包含当前节点所属模块元数据、物理投影字段集、SQL 过滤条件、排序列表及下级子节点执行计划。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryNodePlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模块 ID */
    private Long moduleId;

    /** 完整模块配置元数据 */
    private SysModuleMetaResp moduleMeta;

    /** 物理主表名 */
    private String primaryTable;

    /** 外键关联父级字段 (仅子节点非空，如 student_id) */
    private String parentForeignKey;

    /** 父表被关联的物理主键/唯一键字段名 (默认为 id) */
    @Builder.Default private String parentKey = "id";

    /** 当前节点自身的用户业务筛选条件与下级反向上卷约束复合后的 Condition (若无任何业务筛选则为 null) */
    private Condition effectiveFilterCondition;

    /** 分页页码 (若不分页则为 null) */
    private Integer pageNo;

    /** 分页大小 (若不分页则为 null) */
    private Integer pageSize;

    /** 经过权限与显式投影过滤后的物理投影字段规格列表 */
    @Builder.Default private List<PhysicalFieldSpec> projectedFields = new ArrayList<>();

    /** 经过类型转换与操作符编译后的 SQL Condition */
    private Condition condition;

    /** 编译后的 ORDER BY 字段序列 */
    @Builder.Default private List<OrderField<?>> orderFields = new ArrayList<>();

    /** 伴生表物理 JOIN 列表 (用于 1:1 或 N:1 伴生关联投影) */
    @Builder.Default private List<JoinTableSpec> companionJoins = new ArrayList<>();

    /** 编译后的下级子模块执行计划树 */
    @Builder.Default private List<QueryNodePlan> children = new ArrayList<>();
}
