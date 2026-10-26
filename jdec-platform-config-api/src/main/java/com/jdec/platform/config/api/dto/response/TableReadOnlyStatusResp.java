package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 表只读状态响应 DTO */
@Data
@Schema(description = "表只读状态响应")
public class TableReadOnlyStatusResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "表名", example = "sys_user")
    private String tableName;

    @Schema(description = "是否只读（0=否，1=是）", example = "0")
    private Integer readOnly;

    @Schema(description = "表类型（PRIMARY=主表，SUB=子表）", example = "PRIMARY")
    private String tableType;
}
