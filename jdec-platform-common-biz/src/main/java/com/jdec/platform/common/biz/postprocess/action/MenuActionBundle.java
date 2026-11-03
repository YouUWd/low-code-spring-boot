package com.jdec.platform.common.biz.postprocess.action;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.api.dto.response.ActionRowItem;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 菜单操作按钮集中承载包 (MenuActionBundle)
 *
 * <p>保证全局操作与行级操作物理隔离，并使定制操作与互动权限在集中调度后各司其职：
 *
 * <ul>
 *   <li>{@link #globalActions}：独立输出的全局工具栏操作 (供页面顶部工具栏直接渲染)，对应 {@code MenuDataResp.globalActions}
 *   <li>{@link #customActions}：独立输出的行级定制操作 (如启停，纯行级)，对应 {@code MenuDataResp.customActions}
 *   <li>{@link #interactionActions}：独立输出的角色交互权限行级操作 (纯行级)，对应 {@code
 *       MenuDataResp.interactionActions}
 * </ul>
 */
@Getter
@Builder
@AllArgsConstructor
public class MenuActionBundle {

    /** 独立输出的全局工具栏操作 (供顶部工具栏直接渲染) */
    private final List<ActionButtonDTO> globalActions;

    /** 独立输出的定制操作列表 (如启停，纯行级) */
    private final List<ActionRowItem> customActions;

    /** 独立输出的角色交互权限行级操作列表 (纯行级) */
    private final List<ActionRowItem> interactionActions;

    /** 构建空的按钮承载包 */
    public static MenuActionBundle empty() {
        return new MenuActionBundle(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    /** 构建无生效按钮的承载包 (均为 null) */
    public static MenuActionBundle none() {
        return new MenuActionBundle(null, null, null);
    }
}
