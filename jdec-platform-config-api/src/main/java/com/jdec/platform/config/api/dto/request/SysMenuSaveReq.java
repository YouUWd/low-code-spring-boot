package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.EnableEnum;
import com.jdec.platform.config.api.enums.MenuCategoryEnum;
import com.jdec.platform.config.api.enums.ShowStatusEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 菜单保存/编辑请求 */
@Data
@Schema(description = "菜单保存/编辑请求")
public class SysMenuSaveReq {

    @AuditField(name = "主键ID", ignore = true)
    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @AuditField(
            name = "父菜单",
            type = FieldType.RELATION,
            target = "sys_menu",
            idField = "id",
            nameField = "title",
            ignore = true)
    @Schema(description = "父级id，顶层为0")
    private Long pid;

    @AuditField(name = "菜单名称", uniqueIdentifier = true)
    @Schema(description = "菜单名称")
    private String title;

    @AuditField(name = "参数")
    @Schema(description = "参数")
    private String param;

    @AuditField(name = "菜单类型", type = FieldType.DICT, dictCategoryAlias = "menuType")
    @Schema(description = "菜单类型.1:导航 2:页面 3:标签 4:表")
    private Integer menuType;

    @AuditField(
            name = "菜单分类",
            type = FieldType.ENUM,
            enumClass = MenuCategoryEnum.class,
            ignore = true)
    @Schema(description = "菜单分类.1:业务菜单 2:系统菜单")
    private Integer category;

    @AuditField(name = "默认图标")
    @Schema(description = "默认图标")
    private String imgDefault;

    @AuditField(name = "选中图标")
    @Schema(description = "选中图标")
    private String imgActive;

    @AuditField(
            name = "关联模块",
            type = FieldType.RELATION,
            target = "sys_module",
            idField = "id",
            nameField = "module_name")
    @Schema(description = "关联模块id")
    private Long moduleId;

    @AuditField(name = "排序值", ignore = true)
    @Schema(description = "排序")
    private Integer sortOrder;

    @AuditField(name = "是否显示", type = FieldType.ENUM, enumClass = ShowStatusEnum.class)
    @Schema(description = "0隐藏，1显示")
    private Integer showed;

    @AuditField(name = "是否启用", type = FieldType.ENUM, enumClass = EnableEnum.class)
    @Schema(description = "1启用，0停用")
    private Integer enabled;

    @AuditField(name = "帮助文档地址")
    @Schema(description = "帮助文档地址")
    private String docUrl;

    @AuditField(name = "帮助文档名称")
    @Schema(description = "帮助文档名称")
    private String docName;

    @AuditField(name = "演示视频地址")
    @Schema(description = "演示视频地址")
    private String videoUrl;

    @AuditField(name = "帮助视频名")
    @Schema(description = "帮助视频名")
    private String videoName;
}
