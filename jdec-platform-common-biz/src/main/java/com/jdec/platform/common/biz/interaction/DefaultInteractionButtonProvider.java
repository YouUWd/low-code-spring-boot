package com.jdec.platform.common.biz.interaction;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.api.dto.response.StatusSimpleResp;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionByModuleResp;
import com.jdec.platform.config.api.dto.response.RoleInteractionPermissionByModuleResp.InteractionPermissionItem;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 统一互动权限按钮默认提供者 (DefaultInteractionButtonProvider)
 *
 * <p>适用范围：{@code "*"} (全平台通用兜底)
 *
 * <p>核心契约与设计原则：
 *
 * <ul>
 *   <li><b>统一权限项，默认全为全局按钮</b>：不区分 applyPermissions、editPermissions、viewPermissions，
 *       配置中心获取到的所有互动权限<b>默认全部作为全局操作 (行无关) 按钮输出</b>。
 *   <li><b>面向对象继承与定制扩展</b>：特定业务模块若需将部分交互权限改为行级操作 (非全局)，只需继承此类， 并重写 {@link
 *       #isRowLevelPermission(InteractionPermissionItem, InteractionActionContext)} 返回 true。
 *   <li><b>行级细粒度控制</b>：针对被定制为非全局 (行级) 的按钮，支持通过 {@link #shouldShowRowButton} 结合行字段状态做展示过滤。
 * </ul>
 */
@Component
@Order(100)
public class DefaultInteractionButtonProvider implements InteractionButtonProvider {

    @Override
    public String target() {
        return "*";
    }

    /**
     * 贡献与行数据无关的全局互动按钮
     *
     * <p>默认逻辑：遍历全部互动权限，所有未被标记为行级权限 (非全局) 的项，统一装配输出为全局工具栏按钮。
     */
    @Override
    public void addGlobalButtons(InteractionActionContext ctx, List<ActionButtonDTO> globalBtns) {
        List<InteractionPermissionItem> allPermissions = getAllPermissions(ctx);
        if (CollectionUtils.isEmpty(allPermissions)) {
            return;
        }

        for (InteractionPermissionItem perm : allPermissions) {
            if (perm == null || perm.getPermissionCode() == null) {
                continue;
            }
            // 默认全为全局：只有被定制声明为非全局 (行级) 的才排除
            if (!isRowLevelPermission(perm, ctx)) {
                globalBtns.add(
                        ActionButtonDTO.builder()
                                .buttonKey(perm.getPermissionCode())
                                .buttonName(perm.getPermissionName())
                                .type(perm.getType())
                                .sort(perm.getSort())
                                .build());
            }
        }
    }

    /**
     * 为单行数据根据配置追加行级互动按钮
     *
     * <p>默认逻辑：仅装配被 {@link #isRowLevelPermission} 显式声明为行级 (非全局) 且满足 {@link #shouldShowRowButton}
     * 显示条件的按钮。
     */
    @Override
    public void addRowButtons(
            DataRow row, InteractionActionContext ctx, List<ActionButtonDTO> rowBtns) {
        if (row == null || ctx == null) {
            return;
        }

        List<InteractionPermissionItem> allPermissions = getAllPermissions(ctx);
        if (CollectionUtils.isEmpty(allPermissions)) {
            return;
        }

        for (InteractionPermissionItem perm : allPermissions) {
            if (perm == null || perm.getPermissionCode() == null) {
                continue;
            }

            // 只有被子类声明为非全局 (行级) 的权限项，才参与行级按钮装配
            if (isRowLevelPermission(perm, ctx)) {
                if (shouldShowRowButton(perm, row, ctx)) {
                    String code = perm.getPermissionCode();
                    String name =
                            perm.getPermissionName() != null ? perm.getPermissionName() : code;
                    rowBtns.add(ActionButtonDTO.of(code, name, perm.getType(), perm.getSort()));
                }
            }
        }
    }

    /**
     * 判断指定交互权限是否为行级权限 (非全局)
     *
     * <p><b>默认规则：返回 false</b>。即不区分 applyPermissions、editPermissions 等，默认全部为全局按钮。 定制业务子类重写此方法，将特定权限
     * (如 edit_company_reg_status) 返回 true，即可将其转为行级按钮。
     *
     * @param perm 当前权限项配置
     * @param ctx 当前交互上下文
     * @return true-行级权限 (非全局，逐行绑定); false-全局权限 (默认)
     */
    protected boolean isRowLevelPermission(
            InteractionPermissionItem perm, InteractionActionContext ctx) {
        return false;
    }

    /**
     * 行级按钮显示判断扩展点 (对于被声明为行级/非全局的按钮，进一步决定当前行是否展示)
     *
     * @param perm 当前权限项配置
     * @param row 当前行物理数据
     * @param ctx 当前交互上下文
     * @return true-当前行展示; false-当前行隐藏
     */
    protected boolean shouldShowRowButton(
            InteractionPermissionItem perm, DataRow row, InteractionActionContext ctx) {
        return true;
    }

    /** 汇集当前模块配置的所有互动权限项 (不区分 applyPermissions、editPermissions、viewPermissions) */
    protected List<InteractionPermissionItem> getAllPermissions(InteractionActionContext ctx) {
        if (ctx == null || ctx.getModulePermissionResp() == null) {
            return Collections.emptyList();
        }
        RoleInteractionPermissionByModuleResp resp = ctx.getModulePermissionResp();
        List<InteractionPermissionItem> all = new ArrayList<>();
        if (resp.getApplyPermissions() != null) {
            all.addAll(resp.getApplyPermissions());
        }
        if (resp.getEditPermissions() != null) {
            all.addAll(resp.getEditPermissions());
        }
        if (resp.getViewPermissions() != null) {
            all.addAll(resp.getViewPermissions());
        }
        return all;
    }

    /** 根据权限编码查找对应的权限项配置 */
    protected InteractionPermissionItem findPermission(
            InteractionActionContext ctx, String permissionCode) {
        if (permissionCode == null || ctx == null) {
            return null;
        }
        for (InteractionPermissionItem item : getAllPermissions(ctx)) {
            if (item != null && permissionCode.equals(item.getPermissionCode())) {
                return item;
            }
        }
        return null;
    }

    /** 兼容性提取当前行的业务状态 (business_status) */
    protected Integer extractBusinessStatus(DataRow row, InteractionActionContext ctx) {
        Object val = null;
        if (ctx != null && ctx.getPrimaryTable() != null) {
            val = row.getValue(ctx.getPrimaryTable(), "business_status");
            if (val == null) {
                val = row.getValue(ctx.getPrimaryTable(), "businessStatus");
            }
        }
        if (val == null && row.getTables() != null) {
            val = row.getTables().get("business_status");
            if (val == null) {
                val = row.getTables().get("businessStatus");
            }
        }
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
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof StatusSimpleResp s) {
            return s.getStatusValue();
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
        if (val instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
