package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 保存角色用户预检查结果 */
@Data
@Schema(description = "保存角色用户预检查结果")
public class CheckRoleUserResp {

    @Schema(description = "是否需要确认")
    private Boolean needConfirm;

    @Schema(description = "确认类型 1-时间覆盖 2-主体覆盖员工(可强制保存) 3-员工已在主体中(不可强制保存)")
    private Integer confirmType;

    @Schema(description = "提示消息")
    private String message;

    @Schema(description = "冲突用户名称列表")
    private List<String> conflictNames;
}
