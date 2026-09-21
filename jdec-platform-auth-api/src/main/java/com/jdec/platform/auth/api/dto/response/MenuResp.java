package com.jdec.platform.auth.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单信息（迁移自 student-manage 的 MenuResp，children/level 由服务端建树时填充） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单信息")
public class MenuResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "菜单标题")
    private String menuTitle;

    @Schema(description = "父级菜单ID，根节点为 0")
    private Long pid;

    @Schema(description = "路由参数")
    private String param;

    @Schema(description = "菜单类型")
    private Integer menuType;

    @Schema(description = "菜单分类：1-业务菜单 2-系统菜单")
    private Integer category;

    @Schema(description = "默认图标")
    private String imgDefault;

    @Schema(description = "选中图标")
    private String imgActive;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "排序值")
    private Integer sortOrder;

    @Schema(description = "是否显示")
    private Integer showed;

    @Schema(description = "文档地址")
    private String docUrl;

    @Schema(description = "文档名称")
    private String docName;

    @Schema(description = "视频地址")
    private String videoUrl;

    @Schema(description = "视频名称")
    private String videoName;

    @Schema(description = "层级，根节点为 0")
    private Integer level;

    @Schema(description = "子菜单")
    private List<MenuResp> children;
}
