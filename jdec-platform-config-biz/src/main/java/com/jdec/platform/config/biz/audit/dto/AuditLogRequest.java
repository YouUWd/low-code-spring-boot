package com.jdec.platform.config.biz.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP审计日志请求体
 *
 * <p>字段与监控服务 addControlLog/addOrderRunLog 接口对齐。 用户相关字段（登录用户/模拟用户/角色/部门/岗位）由 {@link
 * com.jdec.platform.config.biz.audit.service.AuditLogSendService} 统一补全。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogRequest {

    /** 主体ID */
    private Long subjectId;

    /** 主体名称 */
    private String subjectName;

    /** 登录用户ID */
    private Long loginUserId;

    /** 登录用户名 */
    private String loginUserName;

    /** 登录人头像 */
    private String loginAvater;

    /** 登录人工号 */
    private String loginUserNumber;

    /** 登录角色ID */
    private Long loginRoleId;

    /** 登录角色名称 */
    private String loginRoleName;

    /** 登录人部门ID（多个ID，用逗号分割） */
    private String loginUserDeptId;

    /** 登录人部门名称（多个名称，用逗号分割） */
    private String loginUserDeptName;

    /** 登录人岗位ID（多个ID，用逗号分割） */
    private String loginUserJobId;

    /** 登录人岗位名称（多个名称，用逗号分割） */
    private String loginUserJobName;

    /** 是否模拟标记（0：否，1：是） */
    private Integer mockFlag;

    /** 模拟用户ID */
    private Long mockUserId;

    /** 模拟用户名称 */
    private String mockUserName;

    /** 模拟用户头像 */
    private String mockUserAvater;

    /** 模拟用户工号 */
    private String mockUserNumber;

    /** IP地址 */
    private String ip;

    /** 模块名称（@DataAudit 注解中的 module 值，如：系统设置） */
    private String moduleName;

    /** 模块ID（通过菜单 param 匹配 href-url 反查菜单获取的关联模块ID） */
    private Long moduleId;

    /** 菜单参数（前端请求头 href-url 原样透传） */
    private String menuParam;

    /** 链接名称（业务子模块名称，如：状态管理） */
    private String hrefName;

    /** 单据ID（记录主键） */
    private Long dataId;

    /** 单据操作名称（如：修改了角色配置、新增了菜单配置） */
    private String controlName;

    /** 操作结果（成功/失败，可为空） */
    private String operatorResult;

    /** 字体颜色（暂时为空，后续从系统配置获取） */
    private String fontColor;

    /** 浏览器 */
    private String browser;

    /** 操作平台 */
    private String platForm;

    /** 日志备注（字段差异JSON字符串） */
    private String logRemark;

    /** 日志时间（格式：yyyy-MM-dd HH:mm:ss） */
    private String logDate;
}
