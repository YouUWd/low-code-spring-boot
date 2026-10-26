package com.jdec.platform.approval.biz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
@Schema(description = "日志中转信息")
public class LogContentDTO implements Serializable {
    @Schema(description = "所属模块ID")
    private Long moduleId;

    @Schema(description = "所属模块名称")
    private String moduleName;

    @Schema(description = "菜单ID")
    private Long menuId;

    @Schema(description = "菜单参数")
    private String menuParam;

    @Schema(description = "链接前端显示名称")
    private String hrefName;

    @Schema(description = "业务数据ID")
    private String dataId;

    @Schema(description = "单据版本号")
    private String orderVersion;

    @Schema(description = "用户操作信息")
    private String controlName;

    @Schema(description = "消息接收人ID")
    private List<Long> messageReceivers;
}
