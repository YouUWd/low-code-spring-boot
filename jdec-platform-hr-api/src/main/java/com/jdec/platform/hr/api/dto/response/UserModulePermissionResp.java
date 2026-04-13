package com.jdec.platform.hr.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户模块权限信息响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserModulePermissionResp {
    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "模块编码")
    private String moduleCode;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "是否可读")
    private Boolean canRead;

    @Schema(description = "是否可创建")
    private Boolean canCreate;

    @Schema(description = "是否可更新")
    private Boolean canUpdate;

    @Schema(description = "是否可删除")
    private Boolean canDelete;

    @Schema(description = "允许的字段列表")
    private List<String> allowedFields;

    @Schema(description = "允许的按钮列表")
    private List<String> allowedButtons;
}
