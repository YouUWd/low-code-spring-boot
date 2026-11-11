package com.jdec.platform.config.biz.init;

import lombok.Data;

/**
 * 按钮初始化节点（对应 init/button.json 中一项）。
 *
 * <p>不含 subjectId / projectNo：二者取自当前项目主体上下文；id 由初始化 JSON 指定并直接落库。
 */
@Data
public class ButtonInitNode {

    /** 主键ID，由初始化 JSON 指定并直接落库 */
    private Long id;

    /** 按钮名称 */
    private String title;

    /** 按钮类型 */
    private String type;

    /** 默认背景颜色 */
    private String defaultBgColor;

    /** 默认字体颜色 */
    private String defaultFontColor;

    /** 默认边框色 */
    private String defaultBorder;

    /** 悬浮背景颜色 */
    private String levitateBgColor;

    /** 悬浮字体颜色 */
    private String levitateFontColor;

    /** 悬浮边框色 */
    private String levitateBorderColor;

    /** 选中背景颜色 */
    private String selectedBgColor;

    /** 选中字体颜色 */
    private String selectedFontColor;

    /** 选中边框色 */
    private String selectedBorderColor;

    /** 是否启用(1:启用。0：不启用) */
    private Integer enabled;
}
