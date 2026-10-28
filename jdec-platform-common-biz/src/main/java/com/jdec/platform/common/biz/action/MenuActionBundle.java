package com.jdec.platform.common.biz.action;

import com.jdec.platform.common.api.dto.response.ApprovalRowItem;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 菜单操作按钮集中承载包 (MenuActionBundle)
 *
 * <p>保证定制操作与互动权限在集中调度后，数据依然独立处理并分别输出到各自的响应字段中：
 *
 * <ul>
 *   <li>{@link #customActions}：独立输出的行级定制操作 (如启停)，对应 {@code MenuDataResp.customActions}
 *   <li>{@link #interactionActions}：独立输出的角色交互权限 (全局申请+行级业务操作)，对应 {@code
 *       MenuDataResp.interactionActions}
 * </ul>
 */
@Getter
@Builder
@AllArgsConstructor
public class MenuActionBundle {

    /** 独立输出的定制操作列表 (如启停) */
    private final List<ApprovalRowItem> customActions;

    /** 独立输出的互动权限操作列表 (全局申请+行级业务操作) */
    private final List<ApprovalRowItem> interactionActions;

    /** 构建空的按钮承载包 */
    public static MenuActionBundle empty() {
        return new MenuActionBundle(Collections.emptyList(), Collections.emptyList());
    }

    /** 构建无生效按钮的承载包 (均为 null) */
    public static MenuActionBundle none() {
        return new MenuActionBundle(null, null);
    }
}
