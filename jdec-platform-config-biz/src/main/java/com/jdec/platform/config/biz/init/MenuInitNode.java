package com.jdec.platform.config.biz.init;

import java.util.List;
import lombok.Data;

/**
 * 菜单初始化节点（对应 init/menu.json）。
 *
 * <p>不含 pid / subjectId / projectNo：pid 由初始化逻辑按父子关系取父节点 id，subjectId 与 projectNo 取自当前项目主体上下文。 id
 * 由初始化 JSON 指定并直接落库。{@code moduleCode} 用于在模块初始化完成后解析为真实的模块ID。
 */
@Data
public class MenuInitNode {

    /** 主键ID，由初始化 JSON 指定并直接落库 */
    private Long id;

    /** 菜单名称 */
    private String title;

    /** 参数 */
    private String param;

    /** 菜单类型.1:导航 2:页面 3:标签 4:表 */
    private Integer menuType;

    /** 结构类型 */
    private String structuralType;

    /** 是否开启个性化.0:否 1:是 */
    private Integer personalizeFlag;

    /** 菜单分类.1:业务菜单 2:系统菜单 */
    private Integer category;

    /** 默认图标 */
    private String imgDefault;

    /** 选中后图标 */
    private String imgActive;

    /** 关联模块编码，初始化时解析为模块ID；为空表示不关联模块 */
    private String moduleCode;

    /** 排序 */
    private Integer sortOrder;

    /** 0隐藏，1显示 */
    private Integer showed;

    /** 1启用，0停用 */
    private Integer enabled;

    /** 帮助文档地址 */
    private String docUrl;

    /** 帮助文档名称 */
    private String docName;

    /** 演示视频地址 */
    private String videoUrl;

    /** 帮助视频名 */
    private String videoName;

    /** 子菜单 */
    private List<MenuInitNode> children;
}
