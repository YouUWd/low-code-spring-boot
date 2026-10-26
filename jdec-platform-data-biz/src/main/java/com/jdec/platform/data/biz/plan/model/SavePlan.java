package com.jdec.platform.data.biz.plan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 全局整树保存执行计划 (SavePlan) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavePlan {

    /** 根模块保存计划节点 */
    private SaveNodePlan rootNodePlan;
}
