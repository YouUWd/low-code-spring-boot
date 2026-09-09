package com.jdec.platform.data.biz.plan.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全局整树查询执行计划 (QueryPlan)
 *
 * <p>由 QueryPlanCompiler 编译请求树与优化器处理后生成，由 QueryPlanExecutor 进行分阶段物理批处理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 根模块查询节点计划 */
    private QueryNodePlan rootNodePlan;
}
