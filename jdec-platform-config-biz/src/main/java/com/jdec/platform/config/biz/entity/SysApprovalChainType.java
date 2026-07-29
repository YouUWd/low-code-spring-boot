package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jdec.platform.shared.audit.annotation.AuditField;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务表-审批链类型表 */
@Data
@TableName("sys_approval_chain_type")
@Schema(description = "审批链类型")
public class SysApprovalChainType implements Serializable {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @AuditField(name = "审批链类型名称", uniqueIdentifier = true)
    @Schema(description = "名称")
    private String title;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "是否默认审批链分类（1：是，0：否）")
    private Integer defaultFlag;

    @Schema(description = "是否启用(1:启用, 0：不启用)")
    private Integer enabled;

    @Schema(description = "所属主体")
    private Long subjectId;

    @Schema(description = "应用编码")
    private String projectNo;

    @Schema(description = "创建人")
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @Schema(description = "创建人名称")
    private String createdName;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdDate;

    @Schema(description = "更改人")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @Schema(description = "更改人名称")
    private String updatedName;

    @Schema(description = "更改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedDate;
}
