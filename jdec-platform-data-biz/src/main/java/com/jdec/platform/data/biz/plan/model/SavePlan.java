package com.jdec.platform.data.biz.plan.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 全局整树保存执行计划 (SavePlan) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavePlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 根模块保存计划节点 */
    private SaveNodePlan rootNodePlan;
}
