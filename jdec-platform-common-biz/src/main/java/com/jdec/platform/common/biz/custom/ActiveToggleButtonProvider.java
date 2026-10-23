package com.jdec.platform.common.biz.custom;

import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 通用数据行启停按钮提供者 (ActiveToggleButtonProvider)
 *
 * <p>当模块开启启停功能（enableActive = 1）且当前用户具备模块编辑权限时生效：
 *
 * <ul>
 *   <li>无 business_status 字段 -> 不添加任何按钮
 *   <li>business_status = 99（启用状态）-> 展示【停用】按钮
 *   <li>business_status = 11（停用状态）-> 展示【启用】按钮
 *   <li>其他业务状态（如待提交、审核中等）-> 不添加启停按钮
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

    /** 业务状态：停用 (11) */
    public static final int STATUS_DEACTIVE = 11;

    private static final Map<String, Object> ACTIVATE_BUTTON =
            Map.of("buttonKey", BTN_KEY_ACTIVATE, "buttonName", BTN_NAME_ACTIVATE);
    private static final Map<String, Object> DEACTIVATE_BUTTON =
            Map.of("buttonKey", BTN_KEY_DEACTIVATE, "buttonName", BTN_NAME_DEACTIVATE);

    @Override
    public String target() {
        return "*";
    }

    @Override
    public boolean supports(CustomActionContext ctx) {
        return ctx != null
                && Integer.valueOf(1).equals(ctx.enableActive())
                && ctx.hasEditPermission();
    }

    @Override
    public void addButtons(DataRow row, CustomActionContext ctx, List<Map<String, Object>> btns) {
        if (row == null) {
            return;
        }
        Integer businessStatus = extractBusinessStatus(row, ctx);
        // 如果没有 business_status 字段，则不添加任何按钮
        if (businessStatus == null) {
            return;
        }

        if (businessStatus == STATUS_ACTIVE) {
            btns.add(DEACTIVATE_BUTTON);
        } else if (businessStatus == STATUS_DEACTIVE) {
            btns.add(ACTIVATE_BUTTON);
        }
    }

    private Integer extractBusinessStatus(DataRow row, CustomActionContext ctx) {
        Object val = null;
        if (ctx != null && ctx.primaryTable() != null) {
            val = row.getValue(ctx.primaryTable(), "business_status");
            if (val == null) {
                val = row.getValue(ctx.primaryTable(), "businessStatus");
            }
        }
        // 若从主表未取到，兼容从顶层 tables 直接取
        if (val == null && row.getTables() != null) {
            val = row.getTables().get("business_status");
            if (val == null) {
                val = row.getTables().get("businessStatus");
            }
        }
        // 若仍未取到且存在 tables，尝试从第一个表 Map 中提取
        if (val == null && row.getTables() != null) {
            for (Object obj : row.getTables().values()) {
                if (obj instanceof Map<?, ?> map) {
                    val = map.get("business_status");
                    if (val == null) {
                        val = map.get("businessStatus");
                    }
                    if (val != null) {
                        break;
                    }
                }
            }
        }
        return parseStatusValue(val);
    }

    private Integer parseStatusValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof StatusSimpleResp s) {
            return s.getStatusValue();
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof Map<?, ?> map) {
            Object sv = map.get("statusValue");
            if (sv == null) {
                sv = map.get("status_value");
            }
            if (sv instanceof Number n) {
                return n.intValue();
            }
            if (sv != null) {
                try {
                    return Integer.parseInt(String.valueOf(sv).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        try {
            return Integer.parseInt(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
