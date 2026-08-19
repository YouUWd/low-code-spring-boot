package com.jdec.platform.monitorlog.biz.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_monitor_systemlog")
@Schema(description = "系统日志")
public class JdecMonitorSystemLog extends JdecMonitorLogBase {

    @Serial private static final long serialVersionUID = 1L;

    @Schema(description = "模块ID")
    @TableField("module_id")
    private Long moduleId;

    @Schema(description = "模块名称;系统设置/元件管理/")
    @TableField("module_name")
    private String moduleName;

    @Schema(description = "菜单参数;菜单配置中的参数内容")
    @TableField("menu_param")
    private String menuParam;

    @Schema(description = "菜单名称（返回显示用，菜单ID对应的展示名称）")
    @TableField("menu_name")
    private String menuName;

    @Schema(description = "超链接文本")
    @TableField("href_name")
    private String hrefName;

    @Schema(description = "菜单ID")
    @TableField("menu_id")
    private Long menuId;

    @Schema(description = "数据表记录ID")
    @TableField("data_id")
    private Long dataId;

    @Schema(description = "单据操作名称;元件审核/权限配置/审批链配置")
    @TableField("control_name")
    private String controlName;

    @Schema(description = "平台信息")
    @TableField("plat_form")
    private String platFrom;

    @Schema(description = "浏览器信息")
    @TableField("browser")
    private String browser;

    @Schema(description = "系统信息")
    @TableField("system_name")
    private String systemName;

    @Schema(description = "日志子类型")
    @TableField("log_sub_type")
    private String logSubType;
}
