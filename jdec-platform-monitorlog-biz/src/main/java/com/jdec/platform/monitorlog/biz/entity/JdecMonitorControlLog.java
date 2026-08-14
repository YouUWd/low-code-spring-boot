package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_monitor_controllog")
@Schema(description = "控制日志/单据操作日志")
public class JdecMonitorControlLog extends JdecMonitorLogBase {

    @Serial private static final long serialVersionUID = 1L;

    @Schema(description = "审批类型：0，自动审批。1，手动审批")
    @TableField("approvalType")
    private Integer approvalType;

    @Schema(description = "动作名称")
    @TableField("actionName")
    private String actionName;

    @Schema(description = "审批动作图标")
    @TableField("actionPath")
    private String actionPath;

    @Schema(description = "审批结果")
    @TableField("actionTypeTitle")
    private String actionTypeTitle;

    @Schema(description = "审批动作类型")
    @TableField("actionType")
    private String actionType;

    @Schema(description = "模块ID")
    @TableField("module_id")
    private Long moduleId;

    @Schema(description = "模块名称;系统设置/元件管理/")
    @TableField("module_name")
    private String moduleName;

    @Schema(description = "菜单ID")
    @TableField("menu_id")
    private Long menuId;

    @Schema(description = "超链接文本")
    @TableField("href_name")
    private String hrefName;

    @Schema(description = "菜单参数;菜单配置中的参数内容")
    @TableField("menu_param")
    private String menuParam;

    @Schema(description = "数据表记录ID")
    @TableField("data_id")
    private Long dataId;

    @Schema(description = "单据版本号")
    @TableField("orderVersion")
    private Integer orderVersion;

    @Schema(description = "单据操作名称;元件审核/权限配置/审批链配置")
    @TableField("control_name")
    private String controlName;

    @Schema(description = "操作结果/执行结果/处理结果;成功/失败")
    @TableField("operator_result")
    private String operatorResult;

    @Schema(description = "字体颜色;获取系统中配置的颜色，默认为黑色")
    @TableField("font_color")
    private String fontColor;

    @Schema(description = "浏览器信息;浏览器信息")
    @TableField("browser")
    private String browser;
}
