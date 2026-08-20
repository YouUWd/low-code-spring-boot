package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.EnableEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 配置项保存/编辑请求 */
@Data
@Schema(description = "配置项保存/编辑请求")
public class SysConfigItemSaveReq {

    @AuditField(name = "配置项ID", ignore = true)
    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @AuditField(
            name = "所属分类",
            type = FieldType.RELATION,
            target = "sys_config_category",
            idField = "category_alias",
            nameField = "label")
    @Schema(description = "分类别名（根节点必填）")
    private String categoryAlias;

    @AuditField(name = "父级ID")
    @Schema(description = "父级ID，0为根节点")
    private Long pid;

    @AuditField(name = "配置项名称", uniqueIdentifier = true)
    @Schema(description = "显示名称（给人看）")
    private String label;

    @AuditField(name = "配置项值")
    @Schema(description = "存储值（给程序用）")
    private String value;

    @AuditField(name = "说明")
    @Schema(description = "说明")
    private String description;

    @AuditField(name = "扩展属性")
    @Schema(description = "扩展属性 颜色/图标等")
    private String extra;

    @AuditField(name = "来源")
    @Schema(description = "1系统内置 2用户自定义")
    private Integer source;

    @AuditField(name = "排序")
    @Schema(description = "排序")
    private Integer sort;

    @AuditField(name = "状态", type = FieldType.ENUM, enumClass = EnableEnum.class)
    @Schema(description = "1启用 0禁用")
    private Integer status;

    @AuditField(name = "应用编码", ignore = true)
    @Schema(description = "应用编码")
    private String projectNo;
}
