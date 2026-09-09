package com.jdec.platform.data.biz.plan.model;

import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import java.io.Serializable;
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
public class SaveNodePlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模块 ID */
    private Long moduleId;

    /** 完整模块配置元数据 */
    private SysModuleMetaResp moduleMeta;

    /** 物理主表名 */
    private String primaryTable;

    /** 外键关联父级字段 (如 student_id) */
    private String parentForeignKey;

    /** 当前模块待落库的行记录列表 */
    @Builder.Default private List<Map<String, Object>> records = new ArrayList<>();

    /** 下级子模块保存计划树 */
    @Builder.Default private List<SaveNodePlan> children = new ArrayList<>();
}
