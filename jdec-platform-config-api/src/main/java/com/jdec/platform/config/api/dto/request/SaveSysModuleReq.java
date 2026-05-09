package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 保存模块基本信息请求 DTO 用于创建或编辑模块的基本信息 */
@Data
@Schema(description = "保存模块基本信息请求")
public class SaveSysModuleReq {

    /** 模块 ID（编辑时使用，创建时为空） */
    @Schema(description = "模块ID（编辑时使用，创建时为空）", example = "1")
    private Long id;

    /** 租户 ID */
    @Schema(
            description = "租户ID",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "tenant-001")
    private String tenantId;

    /** 模块唯一编码，如 MOD-STUDENT-FULL */
    @Schema(
            description = "模块唯一编码",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "MOD-STUDENT-FULL")
    private String moduleCode;

    /** 模块名称 */
    @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "学生管理")
    private String moduleName;

    /** 模块描述 */
    @Schema(description = "模块描述", example = "学生信息管理模块")
    private String moduleDesc;

    /** 父模块 ID，NULL 表示根模块 */
    @Schema(description = "父模块ID（NULL表示根模块）", example = "1")
    private Long parentId;

    /** 主表名称，folder 类型可为空 */
    @Schema(description = "主表名称", example = "student")
    private String primaryTable;

    /** 模块类型: LIST-列表, DETAIL-详情 */
    @Schema(
            description = "模块类型",
            example = "LIST",
            allowableValues = {"LIST", "DETAIL"})
    private String moduleType;

    /** 同级排序顺序，数字越小越靠前 */
    @Schema(description = "排序顺序（数字越小越靠前）", example = "1")
    private Integer sortOrder;

    /** 列表表头配置（仅LIST类型模块需要） */
    @Schema(description = "列表表头配置（仅LIST类型模块需要）")
    private List<TableHeaderColumn> tableHeader;

    /** 创建人 ID */
    @Schema(description = "创建人ID", example = "user-001")
    private Long createdBy;

    /** 创建人姓名 */
    @Schema(description = "创建人姓名", example = "张三")
    private String createdName;
}
