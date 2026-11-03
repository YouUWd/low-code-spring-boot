package com.jdec.platform.dataengine.biz.operatelog.convert;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.operatelog.dto.ActionChangeLogDTO;
import com.jdec.platform.dataengine.biz.operatelog.dto.ChangeLogStructure;
import com.jdec.platform.dataengine.biz.operatelog.dto.ColumnChangeLogDTO;
import com.jdec.platform.dataengine.biz.operatelog.dto.ModuleChangeLogDTO;
import com.jdec.platform.dataengine.biz.operatelog.dto.OperateLogRemarkDTO;
import com.jdec.platform.dataengine.biz.operatelog.dto.RowChangeLogDTO;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.JSON;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;

/**
 * 操作日志 remark 生成器：把「保存前的旧数据」与「本次保存提交的新数据」对比，生成文档约定的 remark 结构。
 *
 * <p>按模块树并行下降：模块按 {@code moduleId} 对齐，数据行按主键 {@code id} 对齐；新行无主键视为新增，
 * 旧行在新数据中不存在视为删除（删除只记录唯一标识字段）。主子模块变更挂到对应父行的 {@code children} 下。
 *
 * <p>数据行名称 {@code name} 取自模块配置的「代表业务唯一性字段」（sys_module_field.biz_key_order）：
 * 优先取旧数据中的字段值，旧数据中缺失时退回本次提交的新值，多字段按「【字段1-字段2-字段3】」拼接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperateLogRemarkConverter {

    /** 数据行名称中唯一标识字段的拼接分隔符 */
    private static final String UNIQUE_NAME_SEPARATOR = "-";

    /** 数据行名称的前缀、后缀（整体包裹唯一标识字段拼接结果，如【W001-张三】） */
    private static final String UNIQUE_NAME_PREFIX = "【";

    private static final String UNIQUE_NAME_SUFFIX = "】";

    /** 多值字段（数组）展示文本的拼接分隔符 */
    private static final String MULTI_VALUE_SEPARATOR = ",";

    /** 字段旧值为 null 时，生成 remark 使用的占位字符串 */
    private static final String NULL_OLD_PLACEHOLDER = "--";

    /** 结构化字段值（引用/词云回填的对象）取展示文本时按顺序匹配的名称属性 */
    private static final List<String> DISPLAY_TEXT_KEYS =
            List.of("name", "displayName", "display_name", "label", "text", "title", "value");

    private final SysMenuApi sysMenuApi;

    private final ObjectMapper objectMapper;

    /**
     * 字段值保持原始类型：数组仍是数组、JSON 对象仍是 JSON 对象，不做字符串化。
     *
     * @param oldData 保存前的旧数据（详情查询结果），纯新增时为 null
     * @param newData 本次保存提交的数据
     * @param moduleCacheMap 模块元数据（含字段、唯一标识、子表、模块名）
     * @param menuId 菜单ID（用于取变更项名称，可为 null）
     */
    public OperateLogRemarkDTO convert(
            NestedModuleDataDTO oldData,
            NestedModuleDataDTO newData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long menuId) {

        ChangeLogStructure change = null;
        if (newData != null
                && newData.getModuleId() != null
                && CollectionUtil.isNotEmpty(moduleCacheMap)) {
            // 顶层模块即主模块：其自身字段变更平铺到 main；其下所有平级子模块提取为 sub 数组
            ModuleChangeLogDTO root =
                    buildModuleChange(oldData, newData, moduleCacheMap, menuId, new HashMap<>());
            if (root != null) {
                change =
                        ChangeLogStructure.builder()
                                .main(extractMainColumns(root))
                                .sub(extractDirectSubModules(root))
                                .build();
            }
        }
        return OperateLogRemarkDTO.builder().change(change).build();
    }

    /**
     * 抽取主模块（顶层模块）下的全部平级子模块，作为 {@code sub} 数组；主模块自身字段对比已平铺到 main， 不在此重复出现。子模块内部仍可通过其自身的 {@code
     * children} 继续嵌套更深层级模块。
     *
     * <p>主模块的子模块数据挂在各数据行的 {@code children} 下（数据行按主键对齐，子模块为行级归属），
     * 故需遍历主模块新增/更新/删除行，汇总其行下子模块；主模块无任何子模块变更时返回 null（不输出空数组）。
     */
    private List<ModuleChangeLogDTO> extractDirectSubModules(ModuleChangeLogDTO root) {
        List<ModuleChangeLogDTO> sub = new ArrayList<>();
        ActionChangeLogDTO actions = root.getActions();
        if (actions != null) {
            collectRowChildren(actions.getI(), sub);
            collectRowChildren(actions.getU(), sub);
            collectRowChildren(actions.getD(), sub);
        }
        return sub.isEmpty() ? null : sub;
    }

    private void collectRowChildren(List<RowChangeLogDTO> rows, List<ModuleChangeLogDTO> target) {
        if (CollectionUtil.isEmpty(rows)) {
            return;
        }
        for (RowChangeLogDTO row : rows) {
            if (CollectionUtil.isNotEmpty(row.getChildren())) {
                target.addAll(row.getChildren());
            }
        }
    }

    /**
     * 抽取主模块（顶层模块）自身的字段变更，平铺为 {@code main} 列表；主模块不展示唯一值字段（行名），仅保留字段级对比。
     *
     * <p>只取主模块自身行的 columns，不含其下子模块的字段变更；主模块无字段变更时返回 null（不输出空数组）。
     */
    private List<ColumnChangeLogDTO> extractMainColumns(ModuleChangeLogDTO root) {
        List<ColumnChangeLogDTO> main = new ArrayList<>();
        ActionChangeLogDTO actions = root.getActions();
        if (actions != null) {
            collectRowColumns(actions.getI(), main);
            collectRowColumns(actions.getU(), main);
            collectRowColumns(actions.getD(), main);
        }
        return main.isEmpty() ? null : main;
    }

    private void collectRowColumns(List<RowChangeLogDTO> rows, List<ColumnChangeLogDTO> target) {
        if (CollectionUtil.isEmpty(rows)) {
            return;
        }
        for (RowChangeLogDTO row : rows) {
            if (CollectionUtil.isNotEmpty(row.getColumns())) {
                target.addAll(row.getColumns());
            }
        }
    }

    private ModuleChangeLogDTO buildModuleChange(
            NestedModuleDataDTO oldNode,
            NestedModuleDataDTO newNode,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long menuId,
            Map<Long, String> menuNameCache) {

        Long moduleId = newNode.getModuleId();
        SysModuleCombineInfo module = moduleCacheMap.get(moduleId);
        if (module == null || module.getModuleInfo() == null) {
            return null;
        }
        String primaryTable = module.getModuleInfo().getPrimaryTable();

        ActionChangeLogDTO actions =
                ActionChangeLogDTO.builder()
                        .i(new ArrayList<>())
                        .u(new ArrayList<>())
                        .d(new ArrayList<>())
                        .build();

        // 虚模块（无主表）不做行级对比，仅递归子模块
        if (StringUtils.isBlank(primaryTable)) {
            buildVirtualModuleChanges(
                    oldNode, newNode, moduleCacheMap, menuId, menuNameCache, actions);
            return finalizeModule(moduleId, module, menuId, menuNameCache, actions);
        }

        List<String> uniqueFields = module.getUniqueNameFields();

        // 旧数据按主键索引
        Map<Long, RecordDataDTO> oldById = new HashMap<>();
        if (oldNode != null && CollectionUtil.isNotEmpty(oldNode.getRecord())) {
            for (RecordDataDTO oldRec : oldNode.getRecord()) {
                Long id =
                        DataPersistenceUtil.getTableRowIdWithTabName(oldRec.getRow(), primaryTable);
                if (id != null) {
                    oldById.put(id, oldRec);
                }
            }
        }

        if (CollectionUtil.isNotEmpty(newNode.getRecord())) {
            for (RecordDataDTO newRec : newNode.getRecord()) {
                Long id =
                        DataPersistenceUtil.getTableRowIdWithTabName(newRec.getRow(), primaryTable);
                if (id != null && oldById.containsKey(id)) {
                    // 更新
                    RecordDataDTO oldRec = oldById.remove(id);
                    RowChangeLogDTO rowLog =
                            buildPreserveUpdateRow(oldRec, newRec, module, uniqueFields);
                    List<ModuleChangeLogDTO> children =
                            buildChildren(
                                    oldRec.getChildren(),
                                    newRec.getChildren(),
                                    moduleCacheMap,
                                    menuId,
                                    menuNameCache);
                    if (rowLog != null) {
                        if (!children.isEmpty()) {
                            rowLog.setChildren(children);
                        }
                        actions.getU().add(rowLog);
                    } else if (!children.isEmpty()) {
                        // 本行无字段变更，但子模块有变更，仍记录该行
                        actions.getU()
                                .add(
                                        RowChangeLogDTO.builder()
                                                .name(
                                                        buildRowName(
                                                                getRowMap(oldRec, primaryTable),
                                                                getRowMap(newRec, primaryTable),
                                                                uniqueFields))
                                                .children(children)
                                                .build());
                    }
                } else {
                    // 新增
                    RowChangeLogDTO rowLog = buildPreserveInsertRow(newRec, module, uniqueFields);
                    List<ModuleChangeLogDTO> children =
                            buildChildren(
                                    null,
                                    newRec.getChildren(),
                                    moduleCacheMap,
                                    menuId,
                                    menuNameCache);
                    if (rowLog == null) {
                        rowLog =
                                RowChangeLogDTO.builder()
                                        .name(
                                                buildRowName(
                                                        null,
                                                        getRowMap(newRec, primaryTable),
                                                        uniqueFields))
                                        .build();
                    }
                    if (!children.isEmpty()) {
                        rowLog.setChildren(children);
                    }
                    if (rowLog.getColumns() != null || rowLog.getChildren() != null) {
                        actions.getI().add(rowLog);
                    }
                }
            }
        }

        // 删除：旧数据中未被匹配的主键，只记录唯一标识字段
        for (RecordDataDTO oldRec : oldById.values()) {
            actions.getD().add(buildDeleteRow(oldRec, module, primaryTable, uniqueFields));
        }

        return finalizeModule(moduleId, module, menuId, menuNameCache, actions);
    }

    /** 虚模块：无主表，仅递归子模块（旧/新记录按顺序对应） */
    private void buildVirtualModuleChanges(
            NestedModuleDataDTO oldNode,
            NestedModuleDataDTO newNode,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long menuId,
            Map<Long, String> menuNameCache,
            ActionChangeLogDTO actions) {

        List<RecordDataDTO> oldRecs = oldNode != null ? oldNode.getRecord() : null;
        List<RecordDataDTO> newRecs = newNode.getRecord();
        if (CollectionUtil.isEmpty(newRecs)) {
            return;
        }
        for (int idx = 0; idx < newRecs.size(); idx++) {
            RecordDataDTO oldRec =
                    (oldRecs != null && idx < oldRecs.size()) ? oldRecs.get(idx) : null;
            List<ModuleChangeLogDTO> children =
                    buildChildren(
                            oldRec != null ? oldRec.getChildren() : null,
                            newRecs.get(idx).getChildren(),
                            moduleCacheMap,
                            menuId,
                            menuNameCache);
            if (!children.isEmpty()) {
                actions.getU().add(RowChangeLogDTO.builder().children(children).build());
            }
        }
    }

    private ModuleChangeLogDTO finalizeModule(
            Long moduleId,
            SysModuleCombineInfo module,
            Long menuId,
            Map<Long, String> menuNameCache,
            ActionChangeLogDTO actions) {
        if (CollectionUtil.isEmpty(actions.getI())) {
            actions.setI(null);
        }
        if (CollectionUtil.isEmpty(actions.getU())) {
            actions.setU(null);
        }
        if (CollectionUtil.isEmpty(actions.getD())) {
            actions.setD(null);
        }
        if (actions.getI() == null && actions.getU() == null && actions.getD() == null) {
            return null;
        }
        return ModuleChangeLogDTO.builder()
                .name(resolveName(module, menuId, menuNameCache))
                .mId(moduleId)
                .actions(actions)
                .build();
    }

    /** 保持原始类型生成更新行明细（数组/对象不做字符串化） */
    private RowChangeLogDTO buildPreserveUpdateRow(
            RecordDataDTO oldRec,
            RecordDataDTO newRec,
            SysModuleCombineInfo module,
            List<String> uniqueFields) {
        String primaryTable = module.getModuleInfo().getPrimaryTable();
        Map<String, Object> oldRow =
                DataPersistenceUtil.getTableRowMapFromWideRow(oldRec.getRow(), primaryTable);
        Map<String, Object> newRow =
                DataPersistenceUtil.getTableRowMapFromWideRow(newRec.getRow(), primaryTable);
        List<ColumnChangeLogDTO> columns =
                buildPreserveColumns(module, primaryTable, oldRow, newRow);
        columns.addAll(buildPreserveSubTableColumns(module, oldRec.getRow(), newRec.getRow()));
        if (columns.isEmpty()) {
            return null;
        }
        return RowChangeLogDTO.builder()
                .name(buildRowName(oldRow, newRow, uniqueFields))
                .columns(columns)
                .build();
    }

    /** 保持原始类型生成新增行明细（数组/对象不做字符串化） */
    private RowChangeLogDTO buildPreserveInsertRow(
            RecordDataDTO newRec, SysModuleCombineInfo module, List<String> uniqueFields) {
        String primaryTable = module.getModuleInfo().getPrimaryTable();
        Map<String, Object> newRow =
                DataPersistenceUtil.getTableRowMapFromWideRow(newRec.getRow(), primaryTable);
        List<ColumnChangeLogDTO> columns = buildPreserveColumns(module, primaryTable, null, newRow);
        columns.addAll(buildPreserveSubTableColumns(module, null, newRec.getRow()));
        if (columns.isEmpty()) {
            return null;
        }
        return RowChangeLogDTO.builder()
                .name(buildRowName(null, newRow, uniqueFields))
                .columns(columns)
                .build();
    }

    private List<ColumnChangeLogDTO> buildPreserveSubTableColumns(
            SysModuleCombineInfo module,
            Map<String, Object> oldWideRow,
            Map<String, Object> newWideRow) {
        List<ColumnChangeLogDTO> columns = new ArrayList<>();
        if (CollectionUtil.isEmpty(module.getSubTables())) {
            return columns;
        }
        for (String subTable : module.getSubTables()) {
            Map<String, Object> oldRow =
                    oldWideRow != null
                            ? DataPersistenceUtil.getTableRowMapFromWideRow(oldWideRow, subTable)
                            : null;
            Map<String, Object> newRow =
                    DataPersistenceUtil.getTableRowMapFromWideRow(newWideRow, subTable);
            columns.addAll(buildPreserveColumns(module, subTable, oldRow, newRow));
        }
        return columns;
    }

    /**
     * 字段明细生成：old/newer 保持原始类型，数组/对象直接落 remark，不再字符串化。
     *
     * <p>oldRow 为 null 表示新增：只记录非空新值；否则仅记录发生变化的字段。
     */
    private List<ColumnChangeLogDTO> buildPreserveColumns(
            SysModuleCombineInfo module,
            String table,
            Map<String, Object> oldRow,
            Map<String, Object> newRow) {
        List<ColumnChangeLogDTO> columns = new ArrayList<>();
        if (CollectionUtil.isEmpty(newRow)) {
            return columns;
        }
        Map<String, ModuleFieldDTO> fieldMap = module.getFieldMapByTable().get(table);
        boolean insert = oldRow == null;
        for (String column : newRow.keySet()) {
            if (CommonConstant.NONE_DIFF_FIELD_SET.contains(column)) {
                continue;
            }
            Object oldValue = insert ? null : normalizeValue(oldRow.get(column));
            Object newerValue = normalizeValue(newRow.get(column));
            if (insert) {
                // 新增行不记录空值（与既有规则一致）
                if (newerValue == null) {
                    continue;
                }
            } else if (isSameValue(oldValue, newerValue)) {
                continue;
            }
            ModuleFieldDTO field = fieldMap != null ? fieldMap.get(column) : null;
            String fullFieldName = DataPersistenceUtil.getFullFieldName(table, column);
            columns.add(
                    ColumnChangeLogDTO.builder()
                            .name(field != null ? field.getDisplayName() : null)
                            .old(nullToPlaceholder(oldValue))
                            .newer(newerValue)
                            .field(fullFieldName)
                            .fieldId(fieldIdByFullName(module, fullFieldName))
                            .build());
        }
        return columns;
    }

    /** 归一化字段值：jOOQ JSON / JSON 字符串转为结构化对象，其余保持原样 */
    private Object normalizeValue(Object value) {
        String json;
        if (value instanceof JSON jooqJson) {
            json = jooqJson.data();
        } else if (value instanceof JSONB jooqJsonb) {
            json = jooqJsonb.data();
        } else if (value instanceof String str) {
            String trimmed = str.trim();
            if (!((trimmed.startsWith("{") && trimmed.endsWith("}"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]")))) {
                return value;
            }
            json = trimmed;
        } else {
            return value;
        }
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 比较字段值：null 与空白视为相同；数组/对象按 JSON 语义比较；其余标量按字面值比较——同为数字时按数值比较 （100 无论 BigDecimal/double/int
     * 均相等），否则按文本比较。
     *
     * <p>旧数据来自快照反序列化（Map/List），新数据来自详情查询（可能是 JsonNode，且引用/词云回填会增删值为 null
     * 的键），直接按节点相等比较会把未变更字段记成变更，而落库序列化又会隐藏 null 值，导致 old 与 newer 看起来一模一样； 因此统一转成 JSON
     * 树后按业务语义比较：忽略对象键顺序、忽略 null 值键的有无、数字按数值比较。
     */
    boolean isSameValue(Object oldValue, Object newerValue) {
        Object oldNormalized = normalizeValue(oldValue);
        Object newerNormalized = normalizeValue(newerValue);
        boolean oldBlank = isBlankValue(oldNormalized);
        boolean newerBlank = isBlankValue(newerNormalized);
        if (oldBlank || newerBlank) {
            return oldBlank && newerBlank;
        }
        if (isStructuredValue(oldNormalized) || isStructuredValue(newerNormalized)) {
            return isSameJson(toJsonNode(oldNormalized), toJsonNode(newerNormalized));
        }
        if (oldNormalized instanceof Number oldNumber
                && newerNormalized instanceof Number newerNumber) {
            try {
                return toBigDecimal(oldNumber).compareTo(toBigDecimal(newerNumber)) == 0;
            } catch (NumberFormatException ignored) {
                // NaN/Infinity 等无法转 BigDecimal，退回文本比较
            }
        }
        return Objects.equals(
                DataPersistenceUtil.getFieldTxtValue(oldNormalized),
                DataPersistenceUtil.getFieldTxtValue(newerNormalized));
    }

    /** JSON 树比较：null/缺失/空白等价，对象忽略值为 null 的键，数组逐项比较，数字按数值比较 */
    private boolean isSameJson(JsonNode oldNode, JsonNode newerNode) {
        boolean oldEmpty = isEmptyJsonNode(oldNode);
        boolean newerEmpty = isEmptyJsonNode(newerNode);
        if (oldEmpty || newerEmpty) {
            return oldEmpty && newerEmpty;
        }
        if (oldNode.isNumber() && newerNode.isNumber()) {
            return toBigDecimal(oldNode.decimalValue())
                            .compareTo(toBigDecimal(newerNode.decimalValue()))
                    == 0;
        }
        if (oldNode.isObject() && newerNode.isObject()) {
            Set<String> fieldNames = new LinkedHashSet<>();
            oldNode.fieldNames().forEachRemaining(fieldNames::add);
            newerNode.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                if (!isSameJson(oldNode.get(fieldName), newerNode.get(fieldName))) {
                    return false;
                }
            }
            return true;
        }
        if (oldNode.isArray() && newerNode.isArray()) {
            if (oldNode.size() != newerNode.size()) {
                return false;
            }
            for (int i = 0; i < oldNode.size(); i++) {
                if (!isSameJson(oldNode.get(i), newerNode.get(i))) {
                    return false;
                }
            }
            return true;
        }
        // 结构与标量互比、对象与数组互比均视为不同值
        if (oldNode.isContainerNode() || newerNode.isContainerNode()) {
            return false;
        }
        return oldNode.asText().equals(newerNode.asText());
    }

    /** 空值节点：节点缺失、JSON null、空白文本 */
    private boolean isEmptyJsonNode(JsonNode node) {
        return node == null
                || node.isNull()
                || node.isMissingNode()
                || (node.isTextual() && StringUtils.isBlank(node.asText()));
    }

    /** Map/List/数组/JsonNode(Object、Array) 视为结构化值，需要按 JSON 语义比较 */
    private boolean isStructuredValue(Object value) {
        if (value instanceof Collection<?>
                || value instanceof Map<?, ?>
                || value.getClass().isArray()) {
            return true;
        }
        return value instanceof JsonNode node && (node.isObject() || node.isArray());
    }

    /** 统一转 JSON 树：JsonNode 原样使用，Map/List/数组转节点，其余标量转值节点 */
    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        try {
            return objectMapper.valueToTree(value);
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(String.valueOf(value));
        }
    }

    /** 数字统一转 BigDecimal 比较：忽略类型与精度差异（100、100.0、100.00 视为同一值） */
    private BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal decimal) {
            return decimal;
        }
        if (number instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        // Double/Float 用 toString 构造，避免二进制精度误差（100.0 → "100.0"）
        return new BigDecimal(number.toString());
    }

    /** 字段旧值为 null 时返回占位字符串 {@code "--"}，便于前端渲染空旧值 */
    private Object nullToPlaceholder(Object value) {
        return value == null ? NULL_OLD_PLACEHOLDER : value;
    }

    /** null、空白字符串、JSON null/空白文本视为同一空值，避免未改动字段因 null/"" 差异进入 remark */
    private boolean isBlankValue(Object value) {
        if (value instanceof JsonNode node) {
            return isEmptyJsonNode(node);
        }
        return value == null || (value instanceof CharSequence text && StringUtils.isBlank(text));
    }

    private RowChangeLogDTO buildDeleteRow(
            RecordDataDTO oldRec,
            SysModuleCombineInfo module,
            String primaryTable,
            List<String> uniqueFields) {
        Map<String, Object> rowMap =
                DataPersistenceUtil.getTableRowMapFromWideRow(oldRec.getRow(), primaryTable);
        Map<String, ModuleFieldDTO> fieldMap = module.getFieldMapByTable().get(primaryTable);
        List<ColumnChangeLogDTO> columns = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(uniqueFields)) {
            for (String column : uniqueFields) {
                if (!rowMap.containsKey(column)) {
                    continue;
                }
                Object oldValue = normalizeValue(rowMap.get(column));
                if (oldValue == null) {
                    continue;
                }
                ModuleFieldDTO field = fieldMap != null ? fieldMap.get(column) : null;
                columns.add(
                        ColumnChangeLogDTO.builder()
                                .name(field != null ? field.getDisplayName() : null)
                                .old(nullToPlaceholder(oldValue))
                                .newer("")
                                .field(DataPersistenceUtil.getFullFieldName(primaryTable, column))
                                .fieldId(fieldIdByColumn(module, primaryTable, column))
                                .build());
            }
        }
        return RowChangeLogDTO.builder()
                .name(buildRowName(rowMap, null, uniqueFields))
                .columns(columns.isEmpty() ? null : columns)
                .build();
    }

    /**
     * 数据行名称：按模块配置的「代表业务唯一性字段」取值，字段值优先取旧数据，旧数据中缺失（新增行或旧详情未返回该字段）时退回新值，按「【字段1-字段2-字段3】」拼接；字段值均为空时返回空串。
     *
     * @param oldRow 旧数据行，新增行为 null
     * @param newRow 本次提交的数据行，删除行为 null
     * @param uniqueFields 模块配置的代表业务唯一性字段（按 biz_key_order 排序）
     */
    private String buildRowName(
            Map<String, Object> oldRow, Map<String, Object> newRow, List<String> uniqueFields) {
        if (CollectionUtil.isEmpty(uniqueFields)) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (String uniqueField : uniqueFields) {
            String value = getDisplayText(oldRow, uniqueField);
            if (StringUtils.isBlank(value)) {
                value = getDisplayText(newRow, uniqueField);
            }
            if (StringUtils.isNotBlank(value)) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return "";
        }
        return UNIQUE_NAME_PREFIX + String.join(UNIQUE_NAME_SEPARATOR, values) + UNIQUE_NAME_SUFFIX;
    }

    private Map<String, Object> getRowMap(RecordDataDTO rec, String primaryTable) {
        if (rec == null || rec.getRow() == null) {
            return null;
        }
        return DataPersistenceUtil.getTableRowMapFromWideRow(rec.getRow(), primaryTable);
    }

    private String getDisplayText(Map<String, Object> row, String field) {
        if (row == null || !row.containsKey(field)) {
            return null;
        }
        return toDisplayText(normalizeValue(row.get(field)));
    }

    /** 字段值转展示文本：结构化值（引用/词云回填的对象或数组）取名称类属性，避免把 JSON 原文拼进数据行名称 */
    private String toDisplayText(Object value) {
        if (value == null) {
            return null;
        }
        if (isStructuredValue(value)) {
            return toDisplayText(toJsonNode(value));
        }
        String text = DataPersistenceUtil.getFieldTxtValue(value);
        return StringUtils.isBlank(text) ? null : text;
    }

    private String toDisplayText(JsonNode node) {
        if (isEmptyJsonNode(node)) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                String text = toDisplayText(item);
                if (StringUtils.isNotBlank(text)) {
                    parts.add(text);
                }
            }
            return String.join(MULTI_VALUE_SEPARATOR, parts);
        }
        for (String key : DISPLAY_TEXT_KEYS) {
            String text = toDisplayText(node.get(key));
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private List<ModuleChangeLogDTO> buildChildren(
            List<NestedModuleDataDTO> oldChildren,
            List<NestedModuleDataDTO> newChildren,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long menuId,
            Map<Long, String> menuNameCache) {
        List<ModuleChangeLogDTO> result = new ArrayList<>();
        if (CollectionUtil.isEmpty(newChildren)) {
            return result;
        }
        for (NestedModuleDataDTO newChild : newChildren) {
            NestedModuleDataDTO oldChild = findChild(oldChildren, newChild.getModuleId());
            ModuleChangeLogDTO child =
                    buildModuleChange(oldChild, newChild, moduleCacheMap, menuId, menuNameCache);
            if (child != null) {
                result.add(child);
            }
        }
        return result;
    }

    private NestedModuleDataDTO findChild(List<NestedModuleDataDTO> children, Long moduleId) {
        if (CollectionUtil.isEmpty(children) || moduleId == null) {
            return null;
        }
        for (NestedModuleDataDTO child : children) {
            if (moduleId.equals(child.getModuleId())) {
                return child;
            }
        }
        return null;
    }

    private String fieldIdByFullName(SysModuleCombineInfo module, String fullFieldName) {
        if (StringUtils.isBlank(fullFieldName) || module.getFieldAllMap() == null) {
            return null;
        }
        ModuleFieldDTO field = module.getFieldAllMap().get(fullFieldName);
        return field != null && field.getId() != null ? String.valueOf(field.getId()) : null;
    }

    private String fieldIdByColumn(SysModuleCombineInfo module, String table, String column) {
        return fieldIdByFullName(module, DataPersistenceUtil.getFullFieldName(table, column));
    }

    private String resolveName(
            SysModuleCombineInfo module, Long menuId, Map<Long, String> menuNameCache) {
        if (menuId != null) {
            String menuName = menuNameCache.get(menuId);
            if (menuName == null && !menuNameCache.containsKey(menuId)) {
                menuName = queryMenuName(menuId);
                if (menuName != null) {
                    menuNameCache.put(menuId, menuName);
                }
            }
            if (StringUtils.isNotBlank(menuName)) {
                return menuName;
            }
        }
        return module.getModuleInfo() != null ? module.getModuleInfo().getModuleName() : null;
    }

    private String queryMenuName(Long menuId) {
        try {
            SysMenuResp menu = sysMenuApi.getMenuById(menuId);
            return menu != null ? menu.getTitle() : null;
        } catch (Exception e) {
            log.warn("查询菜单名称失败，menuId={}", menuId, e);
            return null;
        }
    }
}
