package com.jdec.platform.data.biz.plan.model;

import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单个模块保存节点的执行计划 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveNodePlan {

    /** 模块 ID */
    private Long moduleId;

    /** 完整模块配置元数据 */
    private ModuleInfo moduleMeta;

    /** 物理主表名 */
    private String primaryTable;

    /** 外键关联父级字段 (如 student_id) */
    private String parentForeignKey;

    /** 当前模块待落库的行记录列表 */
    @Builder.Default private List<Map<String, Object>> records = new ArrayList<>();

    /** 原始源数据行引用列表 (用于落库后回填生成的主键与外键) */
    @Builder.Default private List<DataRow> sourceDataRows = new ArrayList<>();

    /** 是否为 1:N 集合模式 (true: 返回 List<Long>, false: 1:1 单实体返回 Long) */
    @Builder.Default private boolean isMany = false;

    /** 下级子模块保存计划树 (模块级/1:1 共享) */
    @Builder.Default private List<SaveNodePlan> children = new ArrayList<>();

    /** 行级别私有子模块保存计划列表 (与 records 数组索引一一对应，用于 1:N 避免孙表交叉污染) */
    @Builder.Default private List<List<SaveNodePlan>> rowChildren = new ArrayList<>();
}
