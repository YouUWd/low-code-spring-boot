package com.jdec.platform.config.biz.init;

import java.util.List;
import lombok.Data;

/**
 * 通用配置项初始化节点（对应 init/config.json 中分类下的 items）。
 *
 * <p>不含 id / pid / categoryAlias：id 与 pid 由初始化逻辑按父子关系生成，categoryAlias 取自所属分类。
 */
@Data
public class ConfigItemInitNode {

    /** 显示名称（给人看） */
    private String label;

    /** 存储值（给程序用） */
    private String value;

    /** 说明 */
    private String description;

    /** 扩展属性 颜色/图标等 */
    private String extra;

    /** 排序 */
    private Integer sort;

    /** 1启用 0禁用 */
    private Integer status;

    /** 子配置项 */
    private List<ConfigItemInitNode> children;
}
