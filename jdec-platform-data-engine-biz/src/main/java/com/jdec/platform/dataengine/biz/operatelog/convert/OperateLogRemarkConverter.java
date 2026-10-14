package com.jdec.platform.dataengine.biz.operatelog.convert;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.dataengine.api.dto.model.FieldChangeDTO;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RowChangeDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.operatelog.dto.ActionChangeLogDTO;
import com.jdec.platform.dataengine.biz.operatelog.dto.ColumnChangeLogDTO;
import com.jdec.platform.dataengine.biz.operatelog.dto.ModuleChangeLogDTO;
import com.jdec.platform.dataengine.biz.operatelog.dto.OperateLogRemarkDTO;
import com.jdec.platform.dataengine.biz.operatelog.dto.RowChangeLogDTO;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

    /**
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
        Long effectiveMenuId = newNode.getMenuId() != null ? newNode.getMenuId() : menuId;
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
                    oldNode, newNode, moduleCacheMap, effectiveMenuId, menuNameCache, actions);
            return finalizeModule(moduleId, module, effectiveMenuId, menuNameCache, actions);
        }

        Map<String, ModuleFieldDTO> fieldMap = module.getFieldMapByTable().get(primaryTable);
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
                            buildUpdateRow(oldRec, newRec, module, fieldMap, uniqueFields);
                    List<ModuleChangeLogDTO> children =
                            buildChildren(
                                    oldRec.getChildren(),
                                    newRec.getChildren(),
                                    moduleCacheMap,
                                    effectiveMenuId,
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
                    RowChangeLogDTO rowLog = buildInsertRow(newRec, module, fieldMap, uniqueFields);
                    List<ModuleChangeLogDTO> children =
                            buildChildren(
                                    null,
                                    newRec.getChildren(),
                                    moduleCacheMap,
                                    effectiveMenuId,
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

        return finalizeModule(moduleId, module, effectiveMenuId, menuNameCache, actions);
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

    private RowChangeLogDTO buildUpdateRow(
            RecordDataDTO oldRec,
            RecordDataDTO newRec,
            SysModuleCombineInfo module,
            Map<String, ModuleFieldDTO> fieldMap,
            List<String> uniqueFields) {
        String primaryTable = module.getModuleInfo().getPrimaryTable();
        RowChangeDTO rowChange =
                DataPersistenceUtil.convertUpdateRecordToRowChangeDTO(
                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                oldRec.getRow(), primaryTable),
                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                newRec.getRow(), primaryTable),
                        primaryTable,
                        fieldMap,
                        uniqueFields,
                        false);
        RowChangeDTO combined =
                DataPersistenceUtil.combineRowChanges(
                        rowChange,
                        buildSubTableChanges(oldRec, newRec, module, uniqueFields, false, false));
        return toRowChangeLog(combined, module);
    }

    private RowChangeLogDTO buildInsertRow(
            RecordDataDTO newRec,
            SysModuleCombineInfo module,
            Map<String, ModuleFieldDTO> fieldMap,
            List<String> uniqueFields) {
        String primaryTable = module.getModuleInfo().getPrimaryTable();
        RowChangeDTO rowChange =
                DataPersistenceUtil.convertInsertRecordToRowChangeDTO(
                        DataPersistenceUtil.getTableRowMapFromWideRow(
                                newRec.getRow(), primaryTable),
                        primaryTable,
                        fieldMap,
                        uniqueFields);
        RowChangeDTO combined =
                DataPersistenceUtil.combineRowChanges(
                        rowChange,
                        buildSubTableChanges(null, newRec, module, uniqueFields, true, false));
        return toRowChangeLog(combined, module);
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
                String oldValue = DataPersistenceUtil.getFieldTxtValue(rowMap.get(column));
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

    private List<RowChangeDTO> buildSubTableChanges(
            RecordDataDTO oldRec,
            RecordDataDTO newRec,
            SysModuleCombineInfo module,
            List<String> uniqueFields,
            boolean insert,
            boolean isDelete) {
        List<RowChangeDTO> subChanges = new ArrayList<>();
        if (CollectionUtil.isEmpty(module.getSubTables())) {
            return subChanges;
        }
        for (String subTable : module.getSubTables()) {
            Map<String, Object> oldRow =
                    oldRec != null
                            ? DataPersistenceUtil.getTableRowMapFromWideRow(
                                    oldRec.getRow(), subTable)
                            : null;
            Map<String, Object> newRow =
                    newRec != null
                            ? DataPersistenceUtil.getTableRowMapFromWideRow(
                                    newRec.getRow(), subTable)
                            : null;
            RowChangeDTO sub =
                    insert
                            ? DataPersistenceUtil.convertInsertRecordToRowChangeDTO(
                                    newRow,
                                    subTable,
                                    module.getFieldMapByTable().get(subTable),
                                    uniqueFields)
                            : DataPersistenceUtil.convertUpdateRecordToRowChangeDTO(
                                    oldRow,
                                    newRow,
                                    subTable,
                                    module.getFieldMapByTable().get(subTable),
                                    uniqueFields,
                                    isDelete);
            if (sub != null && CollectionUtil.isNotEmpty(sub.getColumns())) {
                subChanges.add(sub);
            }
        }
        return subChanges;
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

    private RowChangeLogDTO toRowChangeLog(RowChangeDTO rowChange, SysModuleCombineInfo module) {
        if (rowChange == null || CollectionUtil.isEmpty(rowChange.getColumns())) {
            return null;
        }
        List<ColumnChangeLogDTO> columns = new ArrayList<>();
        for (FieldChangeDTO fieldChange : rowChange.getColumns()) {
            columns.add(
                    ColumnChangeLogDTO.builder()
                            .name(fieldChange.getName())
                            .old(fieldChange.getOld())
                            .newer(fieldChange.getNewer())
                            .field(fieldChange.getField())
                            .fieldId(fieldIdByFullName(module, fieldChange.getField()))
                            .build());
        }
        return RowChangeLogDTO.builder().name(rowChange.getName()).columns(columns).build();
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
