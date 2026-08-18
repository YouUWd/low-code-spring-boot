package com.jdec.platform.monitorlog.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "监控日志响应")
public class MonitorLogPageListResp {

    @Schema(description = "ID;自增主键")
    private Long id;

    @Schema(description = "主体ID;主题ID（多主体、多租户）")
    private Long subjectId;

    @Schema(description = "主体名称")
    private String subjectName;

    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "审批动作图标")
    private String actionPath;

    @Schema(description = "动作类型")
    private String actionType;

    @Schema(description = "审批结果")
    private String actionTypeTitle;

    @Schema(description = "审批类型：0，自动审批。1，手动审批")
    private Integer approvalType;

    @Schema(description = "登录用户ID;登录用户ID")
    private Long loginUserId;

    @Schema(description = "登录用户名")
    private String loginUserName;

    @Schema(description = "登录人头像")
    private String loginAvater;

    @Schema(description = "登录人工号")
    private String loginUserNumber;

    @Schema(description = "登录用户的角色ID;登录用户角色ID")
    private Long loginRoleId;

    @Schema(description = "登录人角色名称")
    private String loginRoleName;

    @Schema(description = "登录人部门ID;多个ID，用逗号分割")
    private String loginUserDeptId;

    @Schema(description = "登录人部门名称;多个名称，用逗号分割")
    private String loginUserDeptName;

    @Schema(description = "登录人岗位ID;多个ID，用逗号分割")
    private String loginUserJobId;

    @Schema(description = "登录人岗位名称;多个名称，用逗号分割")
    private String loginUserJobName;

    @Schema(description = "是否mock标记;是否mock标记：0，否；1 是")
    private Integer mockFlag;

    @Schema(description = "模拟用户ID;模拟用户ID")
    private Long mockUserId;

    @Schema(description = "模拟用户名称")
    private String mockUserName;

    @Schema(description = "模拟用户头像")
    private String mockUserAvater;

    @Schema(description = "模拟用户工号")
    private String mockUserNumber;

    @Schema(description = "日志发生的IP地址;日志发生的IP地址")
    private String ip;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称;系统设置/元件管理/")
    private String moduleName;

    @Schema(description = "单据编号;审批链配置/FHSQ2409230955401/权限配置")
    private String orderId;

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "菜单参数;菜单配置中的参数内容")
    private String menuParam;

    @Schema(description = "超链接文本")
    private String hrefName;

    @Schema(description = "数据表记录ID")
    private Long dataId;

    @Schema(description = "单据版本号")
    private Integer orderVersion;

    @Schema(description = "单据操作名称;元件审核/权限配置/审批链配置")
    private String controlName;

    @Schema(description = "操作结果/执行结果/处理结果;成功/失败")
    private String operatorResult;

    @Schema(description = "字体颜色;获取系统中配置的颜色，默认为黑色")
    private String fontColor;

    @Schema(description = "浏览器信息;浏览器信息")
    private String browser;

    @Schema(description = "平台信息;平台信息")
    private String platForm;

    @Schema(description = "系统名称;系统名称")
    private String systemName;

    @Schema(description = "日志内容;日志内容")
    private String logContent;

    @Schema(description = "日志备注(JSON格式)")
    private String logRemark;

    @Schema(description = "日志时间;日志时间")
    private LocalDateTime logDate;

    @Schema(description = "日志类型: message_log/control_log/system_log")
    private String logType;

    @Schema(description = "日志类型显示名称（来自系统配置的中文 label，如：系统日志/消息日志/操作日志）")
    private String logTypeLabel;

    @Schema(description = "日志子类型: systemLog/systemNavLog/messageLog/operationLog")
    private String logSubType;
}
