package com.jdec.platform.config.biz.reference;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

/** 一条引用违规明细：某个被删目标 ID 被某张引用表的若干记录引用。 */
@Data
@Builder
public class ReferenceViolation {

    /** 被删除的目标数据键（主键 id 或其它被引用列的值）。 */
    private Object targetId;

    /** 引用方表名。 */
    private String sourceTable;

    /** 引用方外键列名。 */
    private String sourceField;

    /** 引用方业务名称（如「菜单」），缺省为表名。 */
    private String sourceName;

    /** 引用它的记录 ID 列表。 */
    private List<Long> sourceIds;

    /** 引用它的记录展示名列表（可能为空）。 */
    private List<String> displayValues;

    /** 生成可读的明细文案，如 {@code 菜单(sys_menu.module_id)：3 条 [1001, 1005, 2003]}。 */
    public String describe() {
        String label = sourceName == null || sourceName.isBlank() ? sourceTable : sourceName;
        StringBuilder sb = new StringBuilder();
        sb.append(label)
                .append('(')
                .append(sourceTable)
                .append('.')
                .append(sourceField)
                .append(")：")
                .append(sourceIds == null ? 0 : sourceIds.size())
                .append(" 条 [")
                .append(
                        sourceIds == null
                                ? ""
                                : sourceIds.stream()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(", ")))
                .append(']');
        if (displayValues != null && !displayValues.isEmpty()) {
            sb.append("，名称：[")
                    .append(
                            displayValues.stream()
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.joining(", ")))
                    .append(']');
        }
        return sb.toString();
    }
}
