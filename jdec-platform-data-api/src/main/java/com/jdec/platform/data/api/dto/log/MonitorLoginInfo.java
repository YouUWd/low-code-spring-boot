package com.jdec.platform.data.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 监控日志登录信息（monitor_log.login_info），落库前由后端序列化为 JSON 字符串写入 login_info 列 */
@Data
@Schema(description = "监控日志登录信息")
public class MonitorLoginInfo implements Serializable {

    @Schema(description = "登录用户id")
    private Long userId;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "员工号")
    private String userNumber;

    @Schema(description = "角色id")
    private Long roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "部门id")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "岗位id")
    private Long jobId;

    @Schema(description = "岗位名称")
    private String jobName;
}
