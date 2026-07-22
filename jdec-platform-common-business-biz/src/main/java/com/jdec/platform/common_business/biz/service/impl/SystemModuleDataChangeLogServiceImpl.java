package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common_business.biz.change.*;
import com.jdec.platform.common_business.biz.entity.TableChangeLog;
import com.jdec.platform.common_business.biz.mapper.TableChangeLogMapper;
import com.jdec.platform.common_business.biz.service.SystemModuleDataChangeLogService;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemModuleDataChangeLogServiceImpl implements SystemModuleDataChangeLogService {

    private final TableChangeLogMapper tableChangeLogMapper;

    @Override
    public TableChangeLog saveLog(ModuleDataChangeContext context) {
        if (context == null || CollectionUtil.isEmpty(context.getDataChangeContextList())) {
            return null;
        }
        try {
            LinkedHashMap<Long, LinkedHashMap<String, List<DataChangeContext>>> dataChangeMap =
                    new LinkedHashMap<>();
            for (DataChangeContext dataChangeContext : context.getDataChangeContextList()) {
                if (!dataChangeMap.containsKey(dataChangeContext.getModuleId())) {
                    dataChangeMap.put(dataChangeContext.getModuleId(), new LinkedHashMap<>());
                }
                if (!dataChangeMap
                        .get(dataChangeContext.getModuleId())
                        .containsKey(dataChangeContext.getTableName())) {
                    dataChangeMap
                            .get(dataChangeContext.getModuleId())
                            .put(dataChangeContext.getTableName(), new ArrayList<>());
                }
                dataChangeMap
                        .get(dataChangeContext.getModuleId())
                        .get(dataChangeContext.getTableName())
                        .add(dataChangeContext);
            }
            OperationChangeDTO operationChangeDTO = new OperationChangeDTO();

            List<ModuleDataChangeDTO> modifyModules = new ArrayList<>();

            for (Map.Entry<Long, LinkedHashMap<String, List<DataChangeContext>>> entry :
                    dataChangeMap.entrySet()) {
                Long moduleId = entry.getKey();
                ModuleDataChangeDTO moduleDataChangeDTO = new ModuleDataChangeDTO();
                moduleDataChangeDTO.setModuleId(moduleId);
                List<TableDataChangeDTO> modifyTables = new ArrayList<>();
                for (Map.Entry<String, List<DataChangeContext>> dataEntry :
                        entry.getValue().entrySet()) {
                    String tableName = dataEntry.getKey();
                    TableDataChangeDTO tableDataChangeDTO = new TableDataChangeDTO();
                    tableDataChangeDTO.setTableName(tableName);
                    List<RowChangeDTO> insertRows = new ArrayList<>();
                    List<RowChangeDTO> updateRows = new ArrayList<>();
                    List<RowChangeDTO> deleteRows = new ArrayList<>();

                    // 一张表，变更的 多行
                    for (DataChangeContext dataChangeContext : dataEntry.getValue()) {
                        // 插入操作
                        if (dataChangeContext.getBefore() == null
                                && dataChangeContext.getAfter() != null) {
                            RowChangeDTO rowChangeDTO =
                                    TableUtils.getInsertOrDeleteRowChange(
                                            dataChangeContext.getAfter(), new HashMap<>(), true);
                            if (rowChangeDTO != null
                                    && CollectionUtil.isNotEmpty(rowChangeDTO.getRow())) {
                                insertRows.add(rowChangeDTO);
                            }

                        } else if (dataChangeContext.getBefore() != null
                                && dataChangeContext.getAfter() != null) {
                            // 更新操作
                            RowChangeDTO rowChangeDTO =
                                    TableUtils.getUpdateFieldChangeRow(
                                            dataChangeContext.getBefore(),
                                            dataChangeContext.getAfter(),
                                            new HashMap<>());
                            if (rowChangeDTO != null
                                    && CollectionUtil.isNotEmpty(rowChangeDTO.getRow())) {
                                updateRows.add(rowChangeDTO);
                            }
                        } else {
                            // 删除操作
                            RowChangeDTO rowChangeDTO =
                                    TableUtils.getInsertOrDeleteRowChange(
                                            dataChangeContext.getBefore(), new HashMap<>(), false);
                            if (rowChangeDTO != null
                                    && CollectionUtil.isNotEmpty(rowChangeDTO.getRow())) {
                                deleteRows.add(rowChangeDTO);
                            }
                        }
                    }
                    if (CollectionUtil.isNotEmpty(insertRows)) {
                        tableDataChangeDTO.setInsertRows(insertRows);
                    }
                    if (CollectionUtil.isNotEmpty(updateRows)) {
                        tableDataChangeDTO.setUpdateRows(updateRows);
                    }
                    if (CollectionUtil.isNotEmpty(deleteRows)) {
                        tableDataChangeDTO.setDeleteRows(deleteRows);
                    }

                    if (CollectionUtil.isNotEmpty(tableDataChangeDTO.getInsertRows())
                            || CollectionUtil.isNotEmpty(tableDataChangeDTO.getUpdateRows())
                            || CollectionUtil.isNotEmpty(tableDataChangeDTO.getDeleteRows())) {
                        modifyTables.add(tableDataChangeDTO);
                    }
                }
                if (CollectionUtil.isNotEmpty(modifyTables)) {
                    moduleDataChangeDTO.setModifyTables(modifyTables);
                }

                if (CollectionUtil.isNotEmpty(moduleDataChangeDTO.getModifyTables())) {
                    modifyModules.add(moduleDataChangeDTO);
                }
            }
            if (CollectionUtil.isNotEmpty(modifyModules)) {
                operationChangeDTO.setModifyModules(modifyModules);
            }
            if (CollectionUtil.isEmpty(operationChangeDTO.getModifyModules())) {
                // 没有任何变更记录,不用保存
                return null;
            }

            TableChangeLog changeLog = new TableChangeLog();
            changeLog.setDataId(context.getDataId());
            changeLog.setModuleId(context.getMainModuleId());
            changeLog.setSubjectId(context.getSubjectId());
            changeLog.setModuleSlug(null);
            changeLog.setModuleName(null);
            changeLog.setMockUserId(context.getMockUserId());
            changeLog.setRealUserId(context.getRealUserId());

            Long operator =
                    (context.getMockUserId() != null
                                    && !context.getRealUserId().equals(context.getMockUserId()))
                            ? context.getMockUserId()
                            : context.getRealUserId();
            changeLog.setCreatedBy(operator);
            changeLog.setUpdatedBy(operator);
            changeLog.setCreatedDate(LocalDateTime.now());
            changeLog.setUpdatedDate(LocalDateTime.now());
            changeLog.setDeleted(Boolean.FALSE);
            changeLog.setChangeContent(JsonUtil.obj2Json(operationChangeDTO));
            if (context.getCallType() == CallTypeEnum.EDITE) {
                tableChangeLogMapper.insert(changeLog);
            }
            // todo  log...

            return changeLog;

        } catch (Exception e) {
            log.error("save log error,context=" + context.toString() + ",e=", e);
        }
        return null;
    }
}
