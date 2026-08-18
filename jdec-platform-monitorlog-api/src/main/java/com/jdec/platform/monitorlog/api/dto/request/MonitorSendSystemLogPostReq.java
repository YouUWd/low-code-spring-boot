package com.jdec.platform.monitorlog.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "发送系统日志请求体")
public class MonitorSendSystemLogPostReq {

    @Size(max = 100, message = "浏览器(browser)长度不能超过100字符")
    @Schema(description = "浏览器", defaultValue = "unknown")
    private String browser = "unknown";

    @NotBlank(message = "系统名称(systemName)不能为空")
    @Size(max = 100, message = "系统名称(systemName)长度不能超过100字符")
    @Schema(description = "系统名称")
    private String systemName;
}
