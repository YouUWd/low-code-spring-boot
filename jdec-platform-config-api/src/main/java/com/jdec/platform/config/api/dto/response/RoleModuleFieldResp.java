package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 角色模块字段权限响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色模块字段权限响应")
public class RoleModuleFieldResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "字段类型: simple-简单字段, combine-组合字段")
    private String fieldType;

    @Schema(description = "字段ID")
    private Long fieldId;

    @Schema(description = "是否可读")
    private Integer readable;

    @Schema(description = "是否可写")
    private Integer writable;

    @Schema(description = "是否可更新")
    private Integer updatable;
}
