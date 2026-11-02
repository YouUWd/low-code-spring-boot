package com.jdec.platform.common.biz.interaction;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.List;

/**
 * 模块互动权限按钮提供者扩展点 (InteractionButtonProvider)
 *
 * <p>遵循面向对象与业务模块解耦原则：
 *
 * <ul>
 *   <li>支持多级 target 目标匹配：{@code "*"} (全局), {@code "projectNo:*"} (项目级), {@code
 *       "projectNo:moduleCode"} (模块级)
 *   <li>兼容两类互动按钮扩展：行无关全局操作 (addGlobalButtons) 与行数据关联操作 (addRowButtons)
 *   <li>各业务模块或项目仅需实现此接口并声明为 @Component 即可自动注册接入
 * </ul>
 */
public interface InteractionButtonProvider {

    /**
     * 绑定的生效目标规则：
     *
     * <ul>
     *   <li>{@code "*"} 或 {@code "*:*"}：全局匹配
     *   <li>{@code "company:*"}：匹配指定项目下的所有模块
     *   <li>{@code "company:COMPANY-ROOT"}：匹配指定项目下的指定模块编码
     *   <li>{@code "COMPANY-ROOT|WBDX"}：使用 {@code '|'} 分隔支持同时匹配多个模块编码或项目规则
     * </ul>
     */
    default String target() {
        return "*";
    }

    /**
     * 判断当前上下文是否启用此提供者
     *
     * @param ctx 互动操作上下文
     * @return true-启用此提供者, false-跳过
     */
    default boolean supports(InteractionActionContext ctx) {
        String target = target();
        if ("*".equals(target) || "*:*".equals(target)) {
            return true;
        }
        if (ctx == null || target == null || target.isBlank()) {
            return false;
        }

        // 原生支持 '|' 分隔多个匹配规则，如 "COMPANY-ROOT|WBDX" 或 "company:M1|company:M2"
        for (String singleTarget : target.split("\\|")) {
            String rule = singleTarget.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if ("*".equals(rule) || "*:*".equals(rule)) {
                return true;
            }
            String pattern = ctx.getProjectNo() + ":" + ctx.getModuleCode();
            if (rule.equalsIgnoreCase(pattern)) {
                return true;
            }
            if (ctx.getProjectNo() != null && rule.equalsIgnoreCase(ctx.getProjectNo() + ":*")) {
                return true;
            }
            if (ctx.getModuleCode() != null) {
                if (rule.equalsIgnoreCase(ctx.getModuleCode())
                        || rule.equalsIgnoreCase("*:" + ctx.getModuleCode())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 贡献与行数据无关的全局互动按钮 (如申请类按钮、批量导出等工具栏操作)
     *
     * @param ctx 互动操作上下文
     * @param globalBtns 全局按钮收集器 (向其中直接 add 按钮模型)
     */
    default void addGlobalButtons(InteractionActionContext ctx, List<ActionButtonDTO> globalBtns) {}

    /**
     * 为单行数据根据行状态计算并追加行级互动按钮 (如启停、行编辑、调整状态等)
     *
     * @param row 当前物理数据行
     * @param ctx 互动操作上下文
     * @param rowBtns 当前行按钮收集器 (向其中直接 add 按钮模型)
     */
    default void addRowButtons(
            DataRow row, InteractionActionContext ctx, List<ActionButtonDTO> rowBtns) {}
}
