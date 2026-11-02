package com.jdec.platform.common.biz.custom;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.List;

/**
 * 行级定制操作按钮提供者扩展点 (CustomButtonProvider)
 *
 * <p>遵循面向对象规范，使用强类型的 {@link ActionButtonDTO}。 后续针对不同 projectNo + moduleCode
 * 的定制按钮，仅需实现此接口并声明为 @Component 即可自动接入。
 */
public interface CustomButtonProvider {

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
     * 判断当前查询上下文是否适用此提供者
     *
     * @param ctx 定制操作上下文
     * @return true-启用此提供者, false-跳过
     */
    default boolean supports(CustomActionContext ctx) {
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
            String pattern = ctx.projectNo() + ":" + ctx.moduleCode();
            if (rule.equalsIgnoreCase(pattern)) {
                return true;
            }
            if (ctx.projectNo() != null && rule.equalsIgnoreCase(ctx.projectNo() + ":*")) {
                return true;
            }
            if (ctx.moduleCode() != null) {
                if (rule.equalsIgnoreCase(ctx.moduleCode())
                        || rule.equalsIgnoreCase("*:" + ctx.moduleCode())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 为单行数据追加定制按钮 (面向对象强类型定义)
     *
     * @param row 当前物理数据行
     * @param ctx 定制操作上下文
     * @param btns 当前行定制按钮收集器 (向其中直接 add ActionButtonDTO)
     */
    void addButtons(DataRow row, CustomActionContext ctx, List<ActionButtonDTO> btns);
}
