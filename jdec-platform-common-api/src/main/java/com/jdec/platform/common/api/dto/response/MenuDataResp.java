package com.jdec.platform.common.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jdec.platform.config.api.dto.response.MenuHeaderColumnResp;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜单动态数据响应模型 (与 MenuQueryReq 呼应，面向前端 UI 隔离层)
 *
 * <p>顶层包含全局菜单多级列头展示配置 (header)、模块权限汇总 (modulePermission)、单据审批流操作列表 (approvalActions)、 行级定制操作列表
 * (customActions)、角色交互权限操作列表 (interactionActions) 与自相似模块数据树载荷 (data)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "菜单动态数据响应模型")
public class MenuDataResp implements Serializable {

    @Schema(description = "菜单多级列头展示配置 (来源自 UserMenuHeaderApi)")
    private MenuHeaderColumnResp header;

    @Schema(description = "模块权限汇总 (1-有权限, 0-无权限)")
    private ModuleSimplePermissionDTO modulePermission;

    @Schema(description = "页面全局操作按钮列表 (供页面顶部工具栏直接渲染)")
    private List<ActionButtonDTO> globalActions;

    @Schema(description = "单据审批流行级操作列表 (纯行级，包含id与btnList)")
    private List<ActionRowItem> approvalActions;

    @Schema(description = "行级定制扩展操作列表 (纯行级，如启停等定制按钮)")
    private List<ActionRowItem> customActions;

    @Schema(description = "角色交互权限行级操作列表 (纯行级，如修改登记状态等按钮)")
    private List<ActionRowItem> interactionActions;

    @Schema(description = "模块数据树载荷")
    private ModuleDataNode data;
}
