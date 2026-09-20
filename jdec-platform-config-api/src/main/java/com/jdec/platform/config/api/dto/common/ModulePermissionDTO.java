package com.jdec.platform.config.api.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模块与字段权限汇总 DTO
 *
 * <p>包含模块自身及子孙模块的权限汇总 (apply/view/edit)，并提供所属全部字段 (含子模块字段) 的字段级权限映射表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模块与字段权限汇总")
public class ModulePermissionDTO implements Serializable {

    @Schema(description = "模块 ID", example = "1001")
    private Long moduleId;

    @Schema(description = "模块是否具有申请/填报权限 (自身及子模块存在 apply=1 的字段)")
    private Boolean apply;

    @Schema(description = "模块是否具有查看权限 (自身及子模块存在 view=1 的字段)")
    private Boolean view;

    @Schema(description = "模块是否具有编辑权限 (自身及子模块存在 edit=1 的字段)")
    private Boolean edit;

    @Schema(description = "字段级权限映射表 (包含自身及子模块的全部字段，fieldId -> FieldPermissionItem)")
    private Map<Long, FieldPermissionItem> fieldPermissions;

    /** 单个字段的权限明细 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "字段权限项")
    public static class FieldPermissionItem implements Serializable {

        @Schema(description = "字段 ID", example = "101")
        private Long fieldId;

        @Schema(description = "字段所属模块 ID", example = "1001")
        private Long moduleId;

        @Schema(description = "是否可申请/填报(新增): false-否, true-是")
        private Boolean apply;

        @Schema(description = "是否可查看/浏览: false-否, true-是")
        private Boolean view;

        @Schema(description = "是否可编辑/修改: false-否, true-是")
        private Boolean edit;
    }
}
