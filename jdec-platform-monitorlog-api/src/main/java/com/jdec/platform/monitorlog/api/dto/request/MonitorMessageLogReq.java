package com.jdec.platform.monitorlog.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "记录监控消息日志请求")
public class MonitorMessageLogReq {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "主体ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "主体ID不能为空")
    @Min(value = 1, message = "主体ID必须大于0")
    private Long subjectId;

    @Schema(description = "主体名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "主体名称不能为空")
    private String subjectName;

    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "审批动作图标")
    private String actionPath;

    @Schema(description = "审批结果")
    private String actionTypeTitle;

    @Schema(description = "登录用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "登录用户ID不能为空")
    @Min(value = 1, message = "登录用户ID必须大于0")
    private Long loginUserId;

    @Schema(description = "登录用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录用户名不能为空")
    private String loginUserName;

    @Schema(description = "登录人头像", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录人头像不能为空")
    private String loginAvater;

    @Schema(description = "登录人工号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录人工号不能为空")
    private String loginUserNumber;

    @Schema(description = "登录角色ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "登录角色ID不能为空")
    @Min(value = 1, message = "登录角色ID必须大于0")
    private Long loginRoleId;

    @Schema(description = "登录角色名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录角色名称不能为空")
    private String loginRoleName;

    @Schema(description = "登录人部门ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "登录人部门ID不能为空")
    @Min(value = 0, message = "登录人部门ID不能为负")
    private Long loginUserDeptId;

    @Schema(description = "登录人部门名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录人部门名称不能为空")
    private String loginUserDeptName;

    @Schema(description = "是否mock标记：0，否；1 是")
    private Integer mockFlag = 0;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "模拟用户名称")
    private String mockUserName;

    @Schema(description = "模拟用户头像")
    private String mockUserAvater;

    @Schema(description = "模拟用户工号")
    private String mockUserNumber;

    @Schema(description = "模拟角色ID")
    private Long mockRoleId;

    @Schema(description = "模拟角色名称")
    private String mockRoleName;

    @Schema(description = "IP地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "IP地址不能为空")
    private String ip;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "单据编号")
    private String orderId;

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "单据ID")
    private Long dataId;

    @Schema(description = "单据版本号")
    private Integer orderVersion;

    @Schema(description = "单据操作名称")
    private String controlAme;

    @Schema(description = "操作结果/执行结果/处理结果")
    private String operatorResult;

    @Schema(description = "字体颜色")
    private String fontColor;

    @Schema(description = "浏览器")
    private String browser = "unknown";

    @Schema(description = "平台信息")
    private String platForm = "unknown";

    @Schema(description = "系统名称")
    private String systemName = "unknown";

    @Schema(description = "日志内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "日志内容不能为空")
    private String logContent;

    @Schema(description = "日志备注", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "日志备注不能为空")
    private String logRemark;

    @Schema(description = "日志时间")
    private LocalDateTime logDate = LocalDateTime.now();
}
