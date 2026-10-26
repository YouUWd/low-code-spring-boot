package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.EnableEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 配置分类保存/编辑请求 */
@Data
@Schema(description = "配置分类保存/编辑请求")
public class SysConfigCategorySaveReq {

    @AuditField(name = "分类ID", ignore = true)
    @Schema(description = "主键ID，为空时新增")
    private Long id;

    @AuditField(name = "分类名称", uniqueIdentifier = true)
    @Schema(description = "分类名称 如：员工状态")
    private String label;

    @AuditField(name = "分类标识")
    @Schema(description = "分类英文标识 如：employee_status")
    private String categoryAlias;

    @AuditField(name = "分类说明")
    @Schema(description = "分类说明")
    private String description;

    @AuditField(name = "格式")
    @Schema(description = "格式：list / tree / kv")
    private String format;

    @AuditField(name = "来源")
    @Schema(description = "1系统内置 2用户自定义")
    private Integer source;

    @AuditField(name = "排序")
    @Schema(description = "排序")
    private Integer sort;

    @AuditField(name = "状态", type = FieldType.ENUM, enumClass = EnableEnum.class)
    @Schema(description = "1启用 0禁用")
    private Integer status;

    @AuditField(name = "主体ID", ignore = true)
    @Schema(description = "主体ID，0表示全局")
    private Long subjectId;
}
