package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.enums.EnableEnum;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 状态启用/停用请求 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "状态启用/停用请求")
public class SysStatusToggleReq {

    @AuditField(name = "状态ID", ignore = true)
    @Schema(description = "状态ID")
    private Long id;

    @AuditField(name = "启用状态", type = FieldType.ENUM, enumClass = EnableEnum.class)
    @Schema(description = "是否启用(1:启用。0：不启用)")
    private Integer enabled;
}
