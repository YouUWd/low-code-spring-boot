package com.jdec.platform.config.biz.init;

import java.util.List;
import lombok.Data;

/**
 * 通用配置分类初始化节点（对应 init/config.json 中一项）。
 *
 * <p>不含 id / projectNo：id 由初始化逻辑生成，projectNo 取自当前项目主体上下文。
 */
@Data
public class ConfigCategoryInitNode {

    /** 分类名称 */
    private String label;

    /** 分类英文标识 */
    private String categoryAlias;

    /** 分类说明 */
    private String description;

    /** 格式：list / tree / kv */
    private String format;

    /** 1系统内置 2用户自定义 */
    private Integer source;

    /** 排序 */
    private Integer sort;

    /** 1启用 0禁用 */
    private Integer status;

    /** 分类下的配置项（树形） */
    private List<ConfigItemInitNode> items;
}
