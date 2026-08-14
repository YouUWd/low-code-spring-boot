package com.jdec.platform.monitorlog.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "记录监控系统导航日志请求体")
public class MonitorSystemNavPostReq {

    @NotBlank(message = "模块名称(moduleName)不能为空")
    @Size(max = 100, message = "模块名称(moduleName)长度不能超过100字符")
    @Schema(description = "模块名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String moduleName;

    @NotBlank(message = "菜单参数(menuParam)不能为空")
    @Schema(description = "菜单参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private String menuParam;

    @NotBlank(message = "菜单名称(hrefName)不能为空")
    @Schema(description = "超链接文本/菜单名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String hrefName;

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "数据表记录ID")
    private Long dataId;

    @NotBlank(message = "操作描述(controlDesc)不能为空")
    @Schema(description = "菜单操作描述", requiredMode = Schema.RequiredMode.REQUIRED)
    private String controlDesc;

    @NotBlank(message = "浏览器(browser)不能为空")
    @Size(max = 100, message = "浏览器(browser)长度不能超过100字符")
    @Schema(description = "浏览器", defaultValue = "unknown")
    private String browser = "unknown";

    @Schema(description = "平台信息", defaultValue = "unknown")
    private String platForm = "unknown";

    @NotBlank(message = "系统名称(systemName)不能为空")
    @Size(max = 100, message = "系统名称(systemName)长度不能超过100字符")
    @Schema(description = "系统名称", defaultValue = "unknown")
    private String systemName = "unknown";
}
