package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 菜单列表响应 */
@Data
@Schema(description = "菜单列表响应")
public class SysMenuResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "父级id")
    private Long pid;

    @Schema(description = "菜单名称")
    private String title;

    @Schema(description = "参数")
    private String param;

    @Schema(description = "菜单类型.1:导航 2:页面 3:标签 4:表")
    private Integer menuType;

    @Schema(description = "菜单分类.1:业务菜单 2:系统菜单")
    private Integer category;

    @Schema(description = "默认图标")
    private String imgDefault;

    @Schema(description = "选中后图标")
    private String imgActive;

    @Schema(description = "关联模块id")
    private Long moduleId;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "0隐藏，1显示")
    private Integer showed;

    @Schema(description = "1启用，0停用")
    private Integer enabled;

    @Schema(description = "帮助文档地址")
    private String docUrl;

    @Schema(description = "帮助文档名称")
    private String docName;

    @Schema(description = "演示视频地址")
    private String videoUrl;

    @Schema(description = "帮助视频名")
    private String videoName;
}
