package com.jdec.platform.dataengine.biz.operatelog.convert;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.operatelog.dto.ActionChangeLogDTO;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperateLogRemarkConverter {

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

        List<ModuleChangeLogDTO> change = new ArrayList<>();
        if (newData != null
                && newData.getModuleId() != null
                && CollectionUtil.isNotEmpty(moduleCacheMap)) {
            ModuleChangeLogDTO root =
                    buildModuleChange(oldData, newData, moduleCacheMap, menuId, new HashMap<>());
            if (root != null) {
                change.add(root);
            }
        }
        return OperateLogRemarkDTO.builder().change(change).build();
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
                                                        DataPersistenceUtil.getUniqueName(
                                                                DataPersistenceUtil
                                                                        .getTableRowMapFromWideRow(
                                                                                newRec.getRow(),
                                                                                primaryTable),
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
                                                DataPersistenceUtil.getUniqueName(
                                                        DataPersistenceUtil
                                                                .getTableRowMapFromWideRow(
                                                                        newRec.getRow(),
                                                                        primaryTable),
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
                .name(DataPersistenceUtil.getUniqueName(newRow, uniqueFields))
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
                .name(DataPersistenceUtil.getUniqueName(newRow, uniqueFields))
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
                            .old(oldValue)
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
     * 比较字段值：null 与空白视为相同；数组/对象按 JSON 结构比较（忽略对象键顺序）； 其余标量按字面值比较——同为数字时按数值比较（100 无论
     * BigDecimal/double/int 均相等），否则按文本比较。
     */
    private boolean isSameValue(Object oldValue, Object newerValue) {
        Object oldNormalized = normalizeValue(oldValue);
        Object newerNormalized = normalizeValue(newerValue);
        boolean oldBlank = isBlankValue(oldNormalized);
        boolean newerBlank = isBlankValue(newerNormalized);
        if (oldBlank || newerBlank) {
            return oldBlank && newerBlank;
        }
        if (isContainerValue(oldNormalized) || isContainerValue(newerNormalized)) {
            return objectMapper
                    .valueToTree(oldNormalized)
                    .equals(objectMapper.valueToTree(newerNormalized));
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

    /** null 与空白字符串视为同一空值，避免未改动字段因 null/"" 差异进入 remark */
    private boolean isBlankValue(Object value) {
        return value == null || (value instanceof CharSequence text && StringUtils.isBlank(text));
    }

    private boolean isContainerValue(Object value) {
        return value instanceof Collection<?>
                || value instanceof Map<?, ?>
                || value.getClass().isArray();
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
                                .old(oldValue)
                                .newer("")
                                .field(DataPersistenceUtil.getFullFieldName(primaryTable, column))
                                .fieldId(fieldIdByColumn(module, primaryTable, column))
                                .build());
            }
        }
        return RowChangeLogDTO.builder()
                .name(DataPersistenceUtil.getUniqueName(rowMap, uniqueFields))
                .columns(columns.isEmpty() ? null : columns)
                .build();
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
