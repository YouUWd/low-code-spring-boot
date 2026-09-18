package com.jdec.platform.config.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 监控日志登录信息（monitor_log.login_info），落库前由数据引擎序列化为 JSON 字符串写入 login_info 列
 *
 * <p>调用方只需传 {@code userId}、{@code roleId} 两个 id，其余展示信息由日志侧跨库查回补全（人力库取姓名、头像、工号、
 * 部门、岗位，配置中心取角色名称），无需自行查询。
 */
@Data
@Schema(description = "监控日志登录信息")
public class MonitorLoginInfo implements Serializable {

    @Schema(description = "登录用户id（调用方传，其余用户信息由日志侧按此id补全）")
    private Long userId;

    @Schema(description = "用户名（日志侧补全，调用方无需传）")
    private String userName;

    @Schema(description = "头像（日志侧补全，调用方无需传）")
    private String avatar;

    @Schema(description = "员工号（日志侧补全，调用方无需传）")
    private String userNumber;

    @Schema(description = "角色id（调用方传，角色名称由日志侧按此id补全）")
    private Long roleId;

    @Schema(description = "角色名称（日志侧补全，调用方无需传）")
    private String roleName;

    @Schema(description = "部门id（日志侧补全，调用方无需传；人力库为多值，取首个）")
    private Long deptId;

    @Schema(description = "部门名称（日志侧补全，调用方无需传）")
    private String deptName;

    @Schema(description = "岗位id（日志侧补全，调用方无需传；人力库为多值，取首个）")
    private Long jobId;

    @Schema(description = "岗位名称（日志侧补全，调用方无需传）")
    private String jobName;
}
