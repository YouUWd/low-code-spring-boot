package com.jdec.platform.common.biz.postprocess.action.custom;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.biz.support.RowValueReader;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.shared.context.UserContext;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 通用数据行启停按钮提供者 (ActiveToggleButtonProvider)
 *
 * <p>按钮显示条件：
 *
 * <ul>
 *   <li>1. 模块开启启停功能（enableActive = 1）
 *   <li>2. 当前用户具备模块的申请权限（hasApplyPermission = true）
 *   <li>3. 单据创建人（created_by）为当前生效用户（模拟登录取 mockUserId，否则取 userId）
 *   <li>4. 业务状态匹配：
 *       <ul>
 *         <li>无 business_status 字段 -> 不添加任何按钮
 *         <li>business_status = 99（启用状态）-> 展示【停用】按钮
 *         <li>business_status = -2（停用状态）-> 展示【启用】按钮
 *         <li>其他业务状态（如待提交、审核中等）-> 不添加启停按钮
 *       </ul>
 * </ul>
 */
@Component
public class ActiveToggleButtonProvider implements CustomButtonProvider {

    public static final String BTN_KEY_ACTIVATE = "activate";
    public static final String BTN_NAME_ACTIVATE = "启用";

    public static final String BTN_KEY_DEACTIVATE = "deactivate";
    public static final String BTN_NAME_DEACTIVATE = "停用";

    /** 业务状态：启用 (99) */
    public static final int STATUS_ACTIVE = 99;

    /** 业务状态：停用 (-2) */
    public static final int STATUS_DEACTIVE = -2;

    public static final ActionButtonDTO ACTIVATE_BUTTON =
            ActionButtonDTO.of(BTN_KEY_ACTIVATE, BTN_NAME_ACTIVATE);
    public static final ActionButtonDTO DEACTIVATE_BUTTON =
            ActionButtonDTO.of(BTN_KEY_DEACTIVATE, BTN_NAME_DEACTIVATE);

    @Override
    public String target() {
        return "*";
    }

    @Override
    public Set<String> requiredRowFields() {
        return Set.of("business_status", "created_by");
    }

    @Override
    public boolean supports(CustomActionContext ctx) {
        return ctx != null
                && Integer.valueOf(1).equals(ctx.enableActive())
                && ctx.hasApplyPermission();
    }

    @Override
    public void addButtons(DataRow row, CustomActionContext ctx, List<ActionButtonDTO> btns) {
        if (row == null) {
            return;
        }
        // 1. 单据创建人才能操作（对比当前生效用户）
        Long effectiveUserId = resolveEffectiveUserId(ctx);
        if (effectiveUserId == null) {
            return;
        }
        String primaryTable = ctx != null ? ctx.primaryTable() : null;
        Long createdBy = RowValueReader.extractCreatedBy(row, primaryTable);
        if (!effectiveUserId.equals(createdBy)) {
            return;
        }

        // 2. 如果没有 business_status 字段，则不添加任何按钮
        Integer businessStatus = RowValueReader.extractBusinessStatus(row, primaryTable);
        if (businessStatus == null) {
            return;
        }

        if (businessStatus == STATUS_ACTIVE) {
            btns.add(DEACTIVATE_BUTTON);
        } else if (businessStatus == STATUS_DEACTIVE) {
            btns.add(ACTIVATE_BUTTON);
        }
    }

    private Long resolveEffectiveUserId(CustomActionContext ctx) {
        if (UserContext.isMockLogin()) {
            return UserContext.getMockUserId();
        }
        return (ctx != null && ctx.userId() != null) ? ctx.userId() : UserContext.getUserId();
    }
}
