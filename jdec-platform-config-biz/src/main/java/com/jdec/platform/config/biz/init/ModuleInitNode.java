package com.jdec.platform.config.biz.init;

import java.util.List;
import lombok.Data;

/**
 * 模块初始化节点（对应 init/module.json）。
 *
 * <p>不含 subjectId / projectNo：subjectId 与 projectNo 取自当前项目主体上下文；id 由初始化 JSON 指定并直接落库。
 */
@Data
public class ModuleInitNode {

    /** 主键ID，由初始化 JSON 指定并直接落库 */
    private Long id;

    /** 模块唯一编码 */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 模块描述 */
    private String moduleDesc;

    /** 模块类别: 1-业务模块, 2-系统模块 */
    private Integer category;

    /** 主表名称 */
    private String primaryTable;

    /** 数据来源主体ID集合 */
    private List<Long> sourceSubjects;

    /** 排序 */
    private Integer sortOrder;

    /** 子模块 */
    private List<ModuleInitNode> children;
}
