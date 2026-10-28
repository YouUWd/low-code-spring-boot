package com.jdec.platform.common.biz.action;

import com.jdec.platform.common.api.dto.response.ActionRowItem;
import com.jdec.platform.common.biz.custom.CustomActionContext;
import com.jdec.platform.common.biz.custom.RowCustomActionService;
import com.jdec.platform.common.biz.interaction.InteractionActionContext;
import com.jdec.platform.common.biz.interaction.InteractionActionService;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionByModuleResp;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 菜单操作按钮集中处理中枢 (MenuActionButtonService)
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li><b>集中调度与代码收敛</b>：统一汇集定制操作 (如启停) 与业务互动权限的生成逻辑，避免业务层到处散落注入与装配代码；
 *   <li><b>数据独立输出</b>：分别独立生成 {@code customActions} 与 {@code interactionActions}，保证前后端契约清晰且完全解耦。
 * </ul>
 */
@Slf4j
@Service
public class MenuActionButtonService {

    private final RowCustomActionService rowCustomActionService;
    private final InteractionActionService interactionActionService;
    private final SysRolePermissionApi sysRolePermissionApi;

    @Autowired
    public MenuActionButtonService(
            RowCustomActionService rowCustomActionService,
            @Autowired(required = false) InteractionActionService interactionActionService,
            @Autowired(required = false) SysRolePermissionApi sysRolePermissionApi) {
        this.rowCustomActionService = rowCustomActionService;
        this.interactionActionService = interactionActionService;
        this.sysRolePermissionApi = sysRolePermissionApi;
    }

    /**
     * 为数据行集中处理并构建所有操作按钮 (分别产出独立的 customActions 与 interactionActions)
     *
     * @param rawRecords 分页物理数据行
     * @param ctx 菜单按钮集中上下文
     * @return 包含独立 customActions 与 interactionActions 的承载包
     */
    public MenuActionBundle buildActions(List<DataRow> rawRecords, MenuActionContext ctx) {
        if (ctx == null) {
            return MenuActionBundle.none();
        }

        // 1. 集中处理定制操作 (如启用/停用按钮)
        List<ActionRowItem> customActions = resolveCustomActions(rawRecords, ctx);

        // 2. 集中处理互动权限操作 (如申请类全局按钮、业务操作类行级按钮)
        List<ActionRowItem> interactionActions = resolveInteractionActions(rawRecords, ctx);

        return new MenuActionBundle(customActions, interactionActions);
    }

    /**
     * 当分页查询短路 (无数据) 时集中构建按钮承载包
     *
     * <p>注：定制操作无数据时为空列表或 null，互动操作可能存在与行无关的全局按钮需要独立呈现。
     */
    public MenuActionBundle buildEmptyActions(MenuActionContext ctx) {
        if (ctx == null) {
            return MenuActionBundle.none();
        }

        CustomActionContext customActionCtx = toCustomActionContext(ctx);
        List<ActionRowItem> emptyCustomActions =
                (rowCustomActionService != null
                                && rowCustomActionService.hasActiveProviders(customActionCtx))
                        ? Collections.emptyList()
                        : null;

        InteractionActionContext interactionCtx = buildInteractionActionContext(ctx);
        List<ActionRowItem> emptyInteractionActions =
                (interactionActionService != null
                                && interactionActionService.hasActiveProviders(interactionCtx))
                        ? interactionActionService.buildInteractionActions(
                                Collections.emptyList(), interactionCtx)
                        : null;

        return new MenuActionBundle(emptyCustomActions, emptyInteractionActions);
    }

    // ==================== 内部集中处理实现 ====================

    private List<ActionRowItem> resolveCustomActions(
            List<DataRow> rawRecords, MenuActionContext ctx) {
        if (rowCustomActionService == null) {
            return null;
        }
        CustomActionContext customActionCtx = toCustomActionContext(ctx);
        return rowCustomActionService.buildCustomActions(rawRecords, customActionCtx);
    }

    private List<ActionRowItem> resolveInteractionActions(
            List<DataRow> rawRecords, MenuActionContext ctx) {
        if (interactionActionService == null) {
            return null;
        }
        InteractionActionContext interactionCtx = buildInteractionActionContext(ctx);
        return interactionActionService.buildInteractionActions(rawRecords, interactionCtx);
    }

    private CustomActionContext toCustomActionContext(MenuActionContext ctx) {
        return new CustomActionContext(
                ctx.getProjectNo(),
                ctx.getRootModuleId(),
                ctx.getModuleCode(),
                ctx.getPrimaryTable(),
                ctx.getEnableActive(),
                ctx.getChangeModuleId(),
                ctx.getUserId(),
                ctx.getRoleId(),
                ctx.isHasEditPermission());
    }

    private InteractionActionContext buildInteractionActionContext(MenuActionContext ctx) {
        RoleInteractionPermissionByModuleResp permResp = null;
        Set<String> assignedCodes = new HashSet<>();

        if (sysRolePermissionApi != null
                && ctx.getRoleId() != null
                && ctx.getRootModuleId() != null) {
            try {
                permResp =
                        sysRolePermissionApi.getRoleInteractionPermissionByModule(
                                ctx.getRoleId(), ctx.getRootModuleId());
                if (permResp != null) {
                    collectPermissionCodes(permResp.getApplyPermissions(), assignedCodes);
                    collectPermissionCodes(permResp.getEditPermissions(), assignedCodes);
                    collectPermissionCodes(permResp.getViewPermissions(), assignedCodes);
                }
            } catch (Exception ex) {
                log.warn(
                        "[MenuActionButtonService] 获取模块交互权限异常: roleId={}, moduleId={}, error={}",
                        ctx.getRoleId(),
                        ctx.getRootModuleId(),
                        ex.getMessage());
            }
        }

        return InteractionActionContext.builder()
                .projectNo(ctx.getProjectNo())
                .rootModuleId(ctx.getRootModuleId())
                .moduleCode(ctx.getModuleCode())
                .primaryTable(ctx.getPrimaryTable())
                .userId(ctx.getUserId())
                .roleId(ctx.getRoleId())
                .hasEditPermission(ctx.isHasEditPermission())
                .modulePermissionResp(permResp)
                .assignedPermissionCodes(assignedCodes)
                .build();
    }

    private void collectPermissionCodes(
            List<RoleInteractionPermissionByModuleResp.InteractionPermissionItem> items,
            Set<String> target) {
        if (items != null) {
            for (RoleInteractionPermissionByModuleResp.InteractionPermissionItem item : items) {
                if (item != null && item.getPermissionCode() != null) {
                    target.add(item.getPermissionCode());
                }
            }
        }
    }
}
