package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.YesNoEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字段配置保存请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段配置保存请求")
public class SysFieldSaveReq {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "列名")
    private String columnName;

    @Schema(description = "前端显示名称")
    @AuditField(name = "字段显示名称")
    private String displayName;

    @Schema(description = "关联关系业务编号")
    @AuditField(name = "关系业务编号")
    private String relationBusinessNo;

    @Schema(description = "关联关系名称")
    @AuditField(name = "关系名称")
    private String relationName;

    @Schema(description = "是否加密存储: 0-否, 1-是")
    @AuditField(name = "字段存储是否加密", type = FieldType.ENUM, enumClass = YesNoEnum.class)
    private Integer encrypted;

    /** 是否生成权限节点.0--否,1--是 */
    @Schema(description = "是否生成权限节点: 0-否, 1-是", example = "0")
    @AuditField(name = "是否生成权限节点", type = FieldType.ENUM, enumClass = YesNoEnum.class)
    private Integer dataRightFlag;

    @Schema(description = "组合配置信息")
    private SysFieldCombineInfoReq combineInfo;

    @Schema(description = "表中文名")
    private String tableCnName;

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "字段业务配置值列表（级联保存）")
    private List<SysFieldValueSaveReq> fieldValues;
}
