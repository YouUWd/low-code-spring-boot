package com.jdec.platform.common.biz.custom;

import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.List;
import java.util.Map;

/**
 * 行级定制操作按钮提供者扩展点 (CustomButtonProvider)
 *
 * <p>后续针对不同 projectNo + moduleCode 的定制按钮，仅需实现此接口并声明为 @Component 即可自动接入。
 */
public interface CustomButtonProvider {

    /**
     * 绑定的生效目标规则：
     *
     * <ul>
     *   <li>{@code "*"} 或 {@code "*:*"}：全局匹配
     *   <li>{@code "company:*"}：匹配指定项目下的所有模块
     *   <li>{@code "company:COMPANY-ROOT"}：匹配指定项目下的指定模块编码
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
        if (ctx == null) {
            return false;
        }
        String pattern = ctx.projectNo() + ":" + ctx.moduleCode();
        if (target.equalsIgnoreCase(pattern)) {
            return true;
        }
        if (target.equalsIgnoreCase(ctx.projectNo() + ":*")) {
            return true;
        }
        return false;
    }

    /**
     * 为单行数据追加定制按钮
     *
     * @param row 当前物理数据行
     * @param ctx 定制操作上下文
     * @param btns 当前行定制按钮收集器 (向其中直接 add 按钮 Map)
     */
    void addButtons(DataRow row, CustomActionContext ctx, List<Map<String, Object>> btns);
}
