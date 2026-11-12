package com.jdec.platform.config.biz.reference;

import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;

/** 一条引用违规明细：某个被删目标 ID 被某项业务数据的若干记录引用。 */
@Data
@Builder
public class ReferenceViolation {

    /** 文案中展示名称的最大条数，超出部分以「等 N 项」收尾。 */
    private static final int MAX_DISPLAY_NAMES = 10;

    /** 被删除的目标数据键（主键 id 或其它被引用列的值）。 */
    private Object targetId;

    /** 引用方表名（仅用于日志等内部场景，不直接出现在用户提示中）。 */
    private String sourceTable;

    /** 引用方外键列名。 */
    private String sourceField;

    /** 引用方业务名称（如「菜单权限」），缺省为表名。 */
    private String sourceName;

    /** 引用它的记录 ID 列表（仅用于统计条数，不在用户提示中展示）。 */
    private List<Long> sourceIds;

    /** 引用它的记录展示名列表（可能为空）。 */
    private List<String> displayValues;

    /** 生成可读的明细文案，如 {@code 菜单权限：35 条，包含【系统管理、用户管理】等 20 项}； 无名称时只输出条数，不罗列记录 ID。 */
    public String describe() {
        String label = sourceName == null || sourceName.isBlank() ? sourceTable : sourceName;
        int count = sourceIds == null ? 0 : sourceIds.size();
        StringBuilder sb = new StringBuilder();
        sb.append(label).append("：").append(count).append(" 条");
        List<String> names =
                displayValues == null
                        ? List.of()
                        : displayValues.stream()
                                .filter(Objects::nonNull)
                                .filter(name -> !name.isBlank())
                                .distinct()
                                .toList();
        if (names.isEmpty()) {
            return sb.toString();
        }
        int limit = Math.min(names.size(), MAX_DISPLAY_NAMES);
        sb.append("，包含【").append(String.join("、", names.subList(0, limit))).append("】");
        if (names.size() > limit) {
            sb.append(" 等 ").append(names.size()).append(" 项");
        }
        return sb.toString();
    }
}
