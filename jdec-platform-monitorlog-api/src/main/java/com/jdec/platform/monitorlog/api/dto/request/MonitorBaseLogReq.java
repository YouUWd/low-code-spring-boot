package com.jdec.platform.monitorlog.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "监控日志请求基类")
public class MonitorBaseLogReq {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "主体名称")
    private String subjectName;

    @Schema(description = "登录用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "登录用户ID不能为空")
    @Min(value = 1, message = "登录用户ID必须大于0")
    private Long loginUserId;

    @Schema(description = "登录用户名（服务端根据loginUserId自动获取）")
    private String loginUserName;

    @Schema(description = "登录人头像（服务端根据loginUserId自动获取）")
    private String loginAvater;

    @Schema(description = "登录人工号（服务端根据loginUserId自动获取）")
    private String loginUserNumber;

    @Schema(description = "登录角色ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "登录角色ID不能为空")
    @Min(value = 1, message = "登录角色ID必须大于0")
    private Long loginRoleId;

    @Schema(description = "登录角色名称（服务端根据loginRoleId自动获取）")
    private String loginRoleName;

    @Schema(description = "登录人部门ID;多个ID，用逗号分割（服务端根据loginUserId自动获取）")
    private String loginUserDeptId;

    @Schema(description = "登录人部门名称;多个名称，用逗号分割（服务端根据loginUserId自动获取）")
    private String loginUserDeptName;

    @Schema(description = "登录人岗位ID;多个ID，用逗号分割（服务端根据loginUserId自动获取）")
    private String loginUserJobId;

    @Schema(description = "登录人岗位名称;多个名称，用逗号分割（服务端根据loginUserId自动获取）")
    private String loginUserJobName;

    @Schema(description = "是否mock标记：0，否；1 是")
    @Min(value = 0, message = "是否mock标记必须大于等于0")
    @Max(value = 1, message = "是否mock标记必须小于等于1")
    private Integer mockFlag = 0;

    @Schema(description = "模拟用户ID")
    private Long mockUserId;

    @Schema(description = "模拟用户名称")
    private String mockUserName;

    @Schema(description = "模拟用户头像")
    private String mockUserAvater;

    @Schema(description = "模拟用户工号")
    private String mockUserNumber;

    @Schema(description = "IP地址")
    private String ip;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "菜单参数")
    private String menuParam;

    @Schema(description = "超链接文本")
    private String hrefName;

    @Schema(description = "数据表记录ID")
    private Long dataId;

    @Schema(description = "单据版本号")
    private Integer orderVersion;

    @Schema(description = "单据操作名称")
    private String controlName;

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

    @Schema(description = "日志内容")
    private String logContent;

    @Schema(description = "日志备注(JSON格式)")
    private String logRemark;

    @Schema(description = "日志时间")
    private LocalDateTime logDate = LocalDateTime.now();
}
