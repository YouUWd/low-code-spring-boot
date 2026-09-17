package com.jdec.platform.data.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 监控日志模拟信息（monitor_log.mock_info），落库前由后端序列化为 JSON 字符串写入 mock_info 列 */
@Data
@Schema(description = "监控日志模拟信息")
public class MonitorMockInfo implements Serializable {

    @Schema(description = "是否mock标记：0-否，1-是", example = "0")
    private Integer flag;

    @Schema(description = "被模拟用户id")
    private Long userId;

    @Schema(description = "被模拟用户名称")
    private String name;

    @Schema(description = "被模拟用户头像")
    private String avatar;

    @Schema(description = "被模拟用户员工号")
    private String userNumber;
}
