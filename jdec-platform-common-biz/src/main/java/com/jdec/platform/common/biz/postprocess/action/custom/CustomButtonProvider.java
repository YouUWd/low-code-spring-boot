package com.jdec.platform.common.biz.postprocess.action.custom;

import com.jdec.platform.common.api.dto.response.ActionButtonDTO;
import com.jdec.platform.common.biz.support.TargetMatcher;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
     * 声明计算当前行定制按钮所必需的物理表字段名集合（如 {@code "business_status"}、{@code "created_by"}）
     *
     * <p>查询阶段将自动在主表查询投影中补齐缺失的依赖列（影子列），并在返回前端前自动剥离。
     *
     * @return 依赖的物理字段列名集合
     */
    default Set<String> requiredRowFields() {
        return Collections.emptySet();
    }

    /**
     * 判断当前查询上下文是否适用此提供者
     *
     * @param ctx 定制操作上下文
     * @return true-启用此提供者, false-跳过
     */
    default boolean supports(CustomActionContext ctx) {
        String target = target();
        if (ctx == null) {
            return "*".equals(target) || "*:*".equals(target);
        }
        return TargetMatcher.matches(target, ctx.projectNo(), ctx.moduleCode());
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
