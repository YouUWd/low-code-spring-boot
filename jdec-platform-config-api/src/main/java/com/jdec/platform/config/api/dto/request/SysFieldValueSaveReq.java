package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.YesNoEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段值配置保存请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段业务配置值保存请求")
public class SysFieldValueSaveReq {

    @Schema(description = "主键ID（更新时必填）")
    private Long id;

    @Schema(description = "模块ID")
    @AuditField(
            name = "模块名称",
            type = FieldType.RELATION,
            target = "sys_module",
            idField = "id",
            nameField = "module_name")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "字段特定配置值")
    @AuditField(name = "字段值")
    private String fieldValue;

    @Schema(description = "关联审批链ID")
    @AuditField(
            name = "审批链分类",
            type = FieldType.RELATION,
            target = "sys_approval_chain_type",
            idField = "id",
            nameField = "title")
    private Long approvalChainTypeId;

    @Schema(description = "显示排序")
    private Integer sortOrder;

    @Schema(description = "是否启用: 0-禁用, 1-启用")
    @AuditField(name = "是否启用", type = FieldType.ENUM, enumClass = YesNoEnum.class)
    private Integer enabled;
}
