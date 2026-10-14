package com.jdec.platform.common.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 监控日志模拟信息（monitor_log.mock_info），落库前由后端序列化为 JSON 字符串写入 mock_info 列
 *
 * <p>调用方只需传 {@code flag} 与 {@code userId}，其余展示信息由日志侧按用户id 查人力库补全，无需自行查询。
 */
@Data
@Schema(description = "监控日志模拟信息")
public class MonitorMockInfo implements Serializable {

    @Schema(description = "是否mock标记：0-否，1-是", example = "0")
    private Integer flag;

    @Schema(description = "被模拟用户id（调用方传，其余信息由日志侧按此id补全）")
    private Long userId;

    @Schema(description = "被模拟用户名称（日志侧补全，调用方无需传）")
    private String userName;

    @Schema(description = "被模拟用户头像（日志侧补全，调用方无需传）")
    private String avatar;

    @Schema(description = "被模拟用户员工号（日志侧补全，调用方无需传）")
    private String userNumber;

    @Schema(description = "部门id（日志侧补全，调用方无需传；人力库为多值，取首个）")
    private Long deptId;

    @Schema(description = "部门名称（日志侧补全，调用方无需传）")
    private String deptName;

    @Schema(description = "岗位id（日志侧补全，调用方无需传；人力库为多值，取首个）")
    private Long jobId;

    @Schema(description = "岗位名称（日志侧补全，调用方无需传）")
    private String jobName;
}
