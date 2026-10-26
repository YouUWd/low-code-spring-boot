package com.jdec.platform.config.biz.reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 引用删除检查器。
 *
 * <p>删除数据前调用 {@link #check(Class, Collection)}，框架按 {@link ReferenceRegistry} 中的引用关系 逐表查询（批量 {@code
 * IN}），返回「哪些表的哪些记录引用了它」。有引用时由调用方阻止删除。
 *
 * <p>注意：本服务须在带 {@code @DataSource(CONFIG_CENTER)} 的方法内被调用，注入的 {@link JdbcTemplate} 会沿线程上下文命中
 * config_center 数据源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceCheckService {

    private final ReferenceRegistry referenceRegistry;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 按主键 id 检查目标数据是否被引用。
     *
     * @param targetEntity 被删除实体类
     * @param targetIds 被删除数据主键集合
     * @return 引用违规明细；无引用时返回空列表
     */
    public List<ReferenceViolation> check(Class<?> targetEntity, Collection<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }
        return doCheck(referenceRegistry.findByTarget(targetEntity), targetIds);
    }

    /**
     * 按指定被引用列检查目标数据是否被引用（支持字符串等非 id 键）。
     *
     * @param targetEntity 被删除实体类
     * @param targetField 被引用实体中作为被引用列的字段名（如 {@code categoryAlias}）
     * @param targetKeys 被删除数据在该列上的取值集合
     * @return 引用违规明细；无引用时返回空列表
     */
    public List<ReferenceViolation> check(
            Class<?> targetEntity, String targetField, Collection<?> targetKeys) {
        if (targetKeys == null || targetKeys.isEmpty()) {
            return List.of();
        }
        return doCheck(referenceRegistry.findByTarget(targetEntity, targetField), targetKeys);
    }

    /** 按引用关系集合执行检查，返回违规明细。 */
    private List<ReferenceViolation> doCheck(
            List<ReferenceMetadata> metadataList, Collection<?> targetKeys) {
        List<Object> keys =
                targetKeys.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .map(key -> (Object) key)
                        .toList();
        if (keys.isEmpty() || metadataList.isEmpty()) {
            return List.of();
        }
        List<ReferenceViolation> violations = new ArrayList<>();
        for (ReferenceMetadata metadata : metadataList) {
            queryReferences(metadata, keys)
                    .forEach(
                            (targetKey, rows) ->
                                    violations.add(
                                            ReferenceViolation.builder()
                                                    .targetId(targetKey)
                                                    .sourceTable(metadata.sourceTable())
                                                    .sourceField(metadata.sourceField())
                                                    .sourceName(metadata.name())
                                                    .sourceIds(
                                                            rows.stream()
                                                                    .map(row -> toLong(row[0]))
                                                                    .filter(Objects::nonNull)
                                                                    .toList())
                                                    .displayValues(
                                                            rows.stream()
                                                                    .map(
                                                                            row ->
                                                                                    row.length > 1
                                                                                            ? toStringOrNull(
                                                                                                    row[
                                                                                                            1])
                                                                                            : null)
                                                                    .filter(Objects::nonNull)
                                                                    .toList())
                                                    .build()));
        }
        return violations;
    }

    /** 生成统一的引用阻断提示文案。 */
    public String buildMessage(String targetName, List<ReferenceViolation> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("无法删除【").append(targetName == null ? "" : targetName).append("】，存在以下引用数据：");
        for (ReferenceViolation violation : violations) {
            sb.append("\n- ").append(violation.describe());
        }
        return sb.toString();
    }

    /** 查询单个引用关系（一条 SQL，批量 IN），返回 目标键 -> 引用方记录行[主键, 展示名]。 */
    private Map<Object, List<Object[]>> queryReferences(
            ReferenceMetadata metadata, List<Object> keys) {
        boolean hasDisplay = StringUtils.hasText(metadata.displayField());
        String placeholders = keys.stream().map(id -> "?").collect(Collectors.joining(","));
        StringBuilder columns = new StringBuilder();
        columns.append(metadata.sourceField())
                .append(" AS src_val, ")
                .append(metadata.sourcePk())
                .append(" AS src_pk");
        if (hasDisplay) {
            columns.append(", ").append(metadata.displayField()).append(" AS src_display");
        }
        String sql =
                "SELECT "
                        + columns
                        + " FROM "
                        + metadata.sourceTable()
                        + " WHERE "
                        + metadata.sourceField()
                        + " IN ("
                        + placeholders
                        + ")";
        Map<Object, List<Object[]>> grouped = new LinkedHashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    Object srcVal = rs.getObject("src_val");
                    if (srcVal == null) {
                        return;
                    }
                    Object[] row =
                            hasDisplay
                                    ? new Object[] {
                                        rs.getObject("src_pk"), rs.getObject("src_display")
                                    }
                                    : new Object[] {rs.getObject("src_pk")};
                    grouped.computeIfAbsent(srcVal, k -> new ArrayList<>()).add(row);
                },
                keys.toArray());
        return grouped;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            log.warn("引用检查：无法将值转换为 Long: {}", value);
            return null;
        }
    }

    private String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
