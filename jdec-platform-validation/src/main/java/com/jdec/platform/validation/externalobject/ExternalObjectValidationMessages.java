package com.jdec.platform.validation.externalobject;

import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <p>提示语在抛出前按顶层模块归拢：同模块项相邻展示、模块间按首次出现顺序保持模块树顺序（例如“人员构成”与 “人员构成 -
 * 其他联系方式”归到一起）；重复的提示只保留第一条（例如同一字段的多行记录命中同一条规则）。
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
     * <p>抛出前先按顶层模块归拢：以首个 {@code " - "} 前的文本作为模块标识，使同模块的提示相邻展示 （例如“人员构成”与“人员构成 -
     * 其他联系方式”归到同一组），模块之间仍按首次出现的顺序保持模块树顺序， 组内保持原有的收集顺序。
     *
     * @throws BusinessException 提示语以换行符拼接，附带的业务数据为 {@link ExternalObjectValidationFailure}
     */
    public void throwIfNotEmpty() {
        if (messages.isEmpty()) {
            return;
        }
        List<String> errors = groupByModule();
        throw new BusinessException(
                ApiCodeEnum.WARNING_EXISTS.getCode(),
                String.join("\n", errors),
                new ExternalObjectValidationFailure(stage.name(), stage.title(), errors));
    }

    /**
     * 按顶层模块归拢提示：同模块项相邻，模块顺序取首次出现的位置。
     *
     * @return 归拢后的提示列表
     */
    private List<String> groupByModule() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String message : messages) {
            groups.computeIfAbsent(topModule(message), key -> new ArrayList<>()).add(message);
        }
        List<String> ordered = new ArrayList<>(messages.size());
        groups.values().forEach(ordered::addAll);
        return ordered;
    }

    /** 取顶层模块：首个 {@code " - "} 之前的文本；不含分隔符时整条提示即作为标识。 */
    private static String topModule(String message) {
        int idx = message.indexOf(" - ");
        return idx < 0 ? message : message.substring(0, idx);
    }
}
