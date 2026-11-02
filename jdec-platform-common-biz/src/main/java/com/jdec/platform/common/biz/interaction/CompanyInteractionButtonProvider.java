package com.jdec.platform.common.biz.interaction;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionByModuleResp.InteractionPermissionItem;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 模块 COMPANY-ROOT (外部对象管理) 互动权限定制提供者 (CompanyInteractionButtonProvider)
 *
 * <p>核心契约：
 *
 * <ul>
 *   <li>目标模块标识：支持使用 {@code '|'} 分隔多个模块编码（如 {@code COMPANY-ROOT|WBDX}）；
 *   <li>继承自 {@link DefaultInteractionButtonProvider}，默认继承通用全局按钮能力；
 *   <li>在全局按钮中排除 {@code edit_company_reg_status}，定制业务在其本身的 Provider ({@link #addRowButtons}) 中独立处理；
 *   <li>定制业务规则：只有当前角色具备权限且单据处于【启用状态 (99)】或【取消状态 (-99)】时，才为该行添加修改登记状态按钮。
 * </ul>
 */
@Component
@Order(10)
public class CompanyInteractionButtonProvider extends DefaultInteractionButtonProvider {

    /** 目标模块标识：支持 '|' 分隔多个模块编码（如 COMPANY-ROOT|WBDX） */
    public static final String MODULE_CODE_COMPANY_ROOT = "COMPANY-ROOT|WBDX";

    /** 目标互动权限编码：修改登记状态 */
    public static final String PERMISSION_EDIT_COMPANY_REG_STATUS = "edit_company_reg_status";

    /** 业务状态：启用 (99) */
    public static final int STATUS_ACTIVE = 99;

    /** 业务状态：取消 (-99) */
    public static final int STATUS_CANCEL = -99;

    @Override
    public String target() {
        return MODULE_CODE_COMPANY_ROOT;
    }

    /** 将 edit_company_reg_status 排除在全局按钮之外，转由本 Provider 自行按行定制处理 */
    @Override
    protected boolean isRowLevelPermission(
            InteractionPermissionItem perm, InteractionActionContext ctx) {
        if (perm != null && PERMISSION_EDIT_COMPANY_REG_STATUS.equals(perm.getPermissionCode())) {
            return true;
        }
        return super.isRowLevelPermission(perm, ctx);
    }

    /**
     * 定制业务在本身 Provider 中处理：
     *
     * <p>仅当当前角色拥有权限，且单据处于【启用 (99)】或【取消 (-99)】状态时，为该行添加修改登记状态按钮。
     */
    @Override
    public void addRowButtons(
            DataRow row, InteractionActionContext ctx, List<ActionButtonDTO> rowBtns) {
        if (row == null || ctx == null || rowBtns == null) {
            return;
        }

        // 1. 检查当前角色是否拥有修改登记状态权限
        if (!ctx.hasPermission(PERMISSION_EDIT_COMPANY_REG_STATUS)) {
            return;
        }

        // 2. 定制业务规则：只有处于启用 (99) 或取消 (-99) 状态的单据才添加此按钮
        Integer businessStatus = extractBusinessStatus(row, ctx);
        if (businessStatus != null
                && (businessStatus == STATUS_ACTIVE || businessStatus == STATUS_CANCEL)) {
            InteractionPermissionItem perm =
                    findPermission(ctx, PERMISSION_EDIT_COMPANY_REG_STATUS);
            String name =
                    (perm != null && perm.getPermissionName() != null)
                            ? perm.getPermissionName()
                            : "外部对象修改登记状态";
            Integer type = perm != null ? perm.getType() : 2;
            Integer sort = perm != null ? perm.getSort() : null;
            rowBtns.add(ActionButtonDTO.of(PERMISSION_EDIT_COMPANY_REG_STATUS, name, type, sort));
        }
    }
}
