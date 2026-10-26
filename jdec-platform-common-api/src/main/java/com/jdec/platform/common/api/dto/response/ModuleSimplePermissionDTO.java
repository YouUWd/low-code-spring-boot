package com.jdec.platform.common.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模块权限简明汇总 (1-有权限, 0-无权限) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块权限简明信息 (1-有权限, 0-无权限)")
public class ModuleSimplePermissionDTO implements Serializable {

    @Schema(description = "是否具有申请/填报权限: 1-是, 0-否", example = "1")
    private Integer apply;

    @Schema(description = "是否具有查看权限: 1-是, 0-否", example = "1")
    private Integer view;

    @Schema(description = "是否具有编辑权限: 1-是, 0-否", example = "1")
    private Integer edit;
}
