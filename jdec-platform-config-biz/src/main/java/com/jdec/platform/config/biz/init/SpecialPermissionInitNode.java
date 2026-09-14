package com.jdec.platform.config.biz.init;

import lombok.Data;

/**
 * 特殊权限初始化节点（对应 init/special_permission.json 中一项）。
 *
 * <p>不含 subjectId / projectNo：subjectId 与 projectNo 取自当前项目主体上下文；id 由初始化 JSON 指定并直接落库。
 */
@Data
public class SpecialPermissionInitNode {

    /** 主键ID，由初始化 JSON 指定并直接落库 */
    private Long id;

    /** 权限节点编码 */
    private String code;

    /** 权限节点名称 */
    private String name;

    /** 权限节点描述 */
    private String description;

    /** 排序 */
    private Integer sort;

    /** 1启用 0禁用 */
    private Integer status;
}
