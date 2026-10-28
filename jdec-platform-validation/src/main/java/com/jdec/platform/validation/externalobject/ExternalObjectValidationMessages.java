package com.jdec.platform.validation.externalobject;

import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 外部对象单层校验的提示收集器。
 *
 * <p>每层校验把全部不通过项写入同一个收集器，层内收集完成后由 {@link #throwIfNotEmpty()} 一次性抛出：
 *
 * <ul>
 *   <li>{@code status}：校验不通过统一使用 {@link ApiCodeEnum#WARNING_EXISTS}（702），与其它业务异常区分；
 *   <li>{@code message}：换行拼接的提示语，沿用现有前端展示方式；
 *   <li>{@code data}：{@link ExternalObjectValidationFailure}，包含校验层级与提示列表，便于前端按层分组展示。
 * </ul>
 *
 * <p>提示语按加入顺序排序，重复的提示只保留第一条（例如同一字段的多行记录命中同一条规则）。
 */
public class ExternalObjectValidationMessages {

    /** 当前所属校验层级。 */
    private final ExternalObjectCheckStage stage;

    /** 已收集的提示语，按加入顺序去重。 */
    private final Set<String> messages = new LinkedHashSet<>();

    public ExternalObjectValidationMessages(ExternalObjectCheckStage stage) {
        this.stage = stage;
    }

    /**
     * 收集一条不通过的提示。
     *
     * @param message 提示语；为空时忽略
     */
    public void add(String message) {
        if (message != null && !message.isBlank()) {
            messages.add(message);
        }
    }

    /**
     * 存在不通过的提示时一次性抛出业务异常。
     *
     * @throws BusinessException 提示语以换行符拼接，附带的业务数据为 {@link ExternalObjectValidationFailure}
     */
    public void throwIfNotEmpty() {
        if (messages.isEmpty()) {
            return;
        }
        List<String> errors = List.copyOf(messages);
        throw new BusinessException(
                ApiCodeEnum.WARNING_EXISTS.getCode(),
                String.join("\n", errors),
                new ExternalObjectValidationFailure(stage.name(), stage.title(), errors));
    }
}
