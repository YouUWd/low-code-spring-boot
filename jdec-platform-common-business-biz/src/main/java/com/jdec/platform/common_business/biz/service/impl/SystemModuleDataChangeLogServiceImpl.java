package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common_business.api.dto.common.ModuleField;
import com.jdec.platform.common_business.biz.change.*;
import com.jdec.platform.common_business.biz.dto.BizLogDTO;
import com.jdec.platform.common_business.biz.entity.TableChangeLog;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.common_business.biz.log.ComBizLogEventPublisher;
import com.jdec.platform.common_business.biz.mapper.TableChangeLogMapper;
import com.jdec.platform.common_business.biz.service.SystemModuleDataChangeLogService;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
            LinkedHashMap<Long, List<DataChangeContext>> dataChangeMap = new LinkedHashMap<>();

            LinkedHashMap<Long, String> moduleNameMap = new LinkedHashMap<>();
            for (DataChangeContext dataChangeContext : context.getDataChangeContextList()) {
                if (!dataChangeMap.containsKey(dataChangeContext.getModuleId())) {
                    dataChangeMap.put(dataChangeContext.getModuleId(), new ArrayList<>());
                }
                dataChangeMap.get(dataChangeContext.getModuleId()).add(dataChangeContext);
                moduleNameMap.put(
                        dataChangeContext.getModuleId(),
                        dataChangeContext.getModuleFilterInfo().getModuleInfo().getModuleName());
            }
            OperationChangeDTO operationChangeDTO = new OperationChangeDTO();

            List<ModuleDataChangeDTO> modifyModules = new ArrayList<>();

            for (Map.Entry<Long, List<DataChangeContext>> entry : dataChangeMap.entrySet()) {
                Long moduleId = entry.getKey();
                ModuleDataChangeDTO moduleDataChangeDTO = new ModuleDataChangeDTO();
                moduleDataChangeDTO.setModuleId(moduleId);
                moduleDataChangeDTO.setModuleName(moduleNameMap.get(moduleId));
                // List<TableDataChangeDTO> modifyTables = new ArrayList<>();

                List<RowChangeDTO> insertRows = new ArrayList<>();
                List<RowChangeDTO> updateRows = new ArrayList<>();
                List<RowChangeDTO> deleteRows = new ArrayList<>();

                DataChangeActionDTO dataChangeActionDTO = new DataChangeActionDTO();

                for (DataChangeContext dataChangeContext : entry.getValue()) {
                    // String tableName = dataEntry.getKey();
                    // TableDataChangeDTO tableDataChangeDTO = new TableDataChangeDTO();
                    // tableDataChangeDTO.setTableName(tableName);

                    // ，变更的 多行
                    List<ModuleField> moduleFieldList =
                            dataChangeContext.getModuleFilterInfo().getAllFields();

                    Map<String, SysFieldDTO> sysFieldMap =
                            moduleFieldList.stream()
                                    .map(x -> covertToSysFieldDTO(x))
                                    .collect(Collectors.toMap(x -> x.getFieldName(), x -> x));

                    // 插入操作
                    if (dataChangeContext.getBefore() == null
                            && dataChangeContext.getAfter() != null) {
                        RowChangeDTO rowChangeDTO =
                                TableUtils.getInsertOrDeleteRowChange(
                                        dataChangeContext.getAfter(), sysFieldMap, true);
                        if (rowChangeDTO != null
                                && CollectionUtil.isNotEmpty(rowChangeDTO.getColumnList())) {
                            insertRows.add(rowChangeDTO);
                        }

                    } else if (dataChangeContext.getBefore() != null
                            && dataChangeContext.getAfter() != null) {
                        // 更新操作
                        RowChangeDTO rowChangeDTO =
                                TableUtils.getUpdateFieldChangeRow(
                                        dataChangeContext.getBefore(),
                                        dataChangeContext.getAfter(),
                                        sysFieldMap);
                        if (rowChangeDTO != null
                                && CollectionUtil.isNotEmpty(rowChangeDTO.getColumnList())) {
                            updateRows.add(rowChangeDTO);
                        }
                    } else {
                        // 删除操作
                        RowChangeDTO rowChangeDTO =
                                TableUtils.getInsertOrDeleteRowChange(
                                        dataChangeContext.getBefore(), sysFieldMap, false);
                        if (rowChangeDTO != null
                                && CollectionUtil.isNotEmpty(rowChangeDTO.getColumnList())) {
                            deleteRows.add(rowChangeDTO);
                        }
                    }
                }

                if (CollectionUtil.isNotEmpty(insertRows)) {
                    dataChangeActionDTO.setInsertRows(insertRows);
                }

                if (CollectionUtil.isNotEmpty(updateRows)) {
                    dataChangeActionDTO.setUpdateRows(updateRows);
                }

                if (CollectionUtil.isNotEmpty(deleteRows)) {
                    dataChangeActionDTO.setDeleteRows(deleteRows);
                }

                if (CollectionUtil.isNotEmpty(dataChangeActionDTO.getInsertRows())
                        || CollectionUtil.isNotEmpty(dataChangeActionDTO.getUpdateRows())
                        || CollectionUtil.isNotEmpty(dataChangeActionDTO.getDeleteRows())) {
                    moduleDataChangeDTO.setActions(dataChangeActionDTO);
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

            BizLogDTO bizLogDTO =
                    BizLogDTO.builder()
                            .logType("operation-log")
                            .projectNo(LoginUserUtil.getProjectNoFromHeader())
                            .token(LoginUserUtil.getTokenFromHeader())
                            .platform(LoginUserUtil.getOsFromHeader())
                            .browser(LoginUserUtil.getBrowserFromHeader())
                            .ip(LoginUserUtil.getIpFromHeader())
                            .loginUserId(context.getRealUserId())
                            .mockUserId(context.getMockUserId())
                            .currentSubjectId(context.getSubjectId())
                            .currentRoleId(context.getRoleId())
                            .logRemark(getLogContentHtml(operationChangeDTO))
                            .menuId(context.getMenuId())
                            .build();
            if (context.getCallType() == CallTypeEnum.CREATE) {
                ComBizLogEventPublisher.publishLogEvent(BizLogEnum.DATA_SAVE, bizLogDTO);
            } else {
                ComBizLogEventPublisher.publishLogEvent(BizLogEnum.DATA_UPDATE, bizLogDTO);
            }
            return changeLog;
        } catch (Exception e) {
            log.error("save log error,context=" + context.toString() + ",e=", e);
        }
        return null;
    }

    @Override
    public String getLogContentHtml(OperationChangeDTO operationChangeDTO) {
        if (CollectionUtil.isEmpty(operationChangeDTO.getModifyModules())) {
            return null;
        }
        StringBuilder html = new StringBuilder();
        for (ModuleDataChangeDTO moduleDataChangeDTO : operationChangeDTO.getModifyModules()) {
            html.append(
                    "<div style=\"display: flex; align-items: flex-start; margin-bottom: 8px; color: #1d2129\">");
            html.append(
                    "<div style=\"background-color: #e5e6eb; width: 64px; padding: 2px 0; margin-right: 8px; text-align: center\">");
            html.append(moduleDataChangeDTO.getModuleName());
            html.append("</div>");
            appendActionHtml(moduleDataChangeDTO.getActions().getInsertRows(), html, "insert");
            appendActionHtml(moduleDataChangeDTO.getActions().getUpdateRows(), html, "update");
            appendActionHtml(moduleDataChangeDTO.getActions().getDeleteRows(), html, "delete");
            html.append("</div>");
        }
        return html.toString();
    }

    private void appendActionHtml(List<RowChangeDTO> rows, StringBuilder html, String label) {
        if (CollectionUtil.isNotEmpty(rows)) {
            html.append("<div>");
            html.append("<div style=\"display: flex; margin-bottom: 8px\">");
            html.append("<div style=\"margin-right: 10px\">");
            if ("insert".equals(label)) {
                html.append("增加");
            } else if ("update".equals(label)) {
                html.append("修改");
            } else {
                html.append("删除");
            }
            html.append("</div>");
            for (RowChangeDTO rowChangeDTO : rows) {
                html.append("<div>");
                html.append("<div style=\"display: flex\">");
                if (StringUtils.isNotEmpty(rowChangeDTO.getUniqName())) {
                    html.append(" <div style=\"margin-right: 10px\">")
                            .append(rowChangeDTO.getUniqName())
                            .append("</div>");
                }
                html.append("<div>");
                for (FieldChangeDTO fieldChangeDTO : rowChangeDTO.getColumnList()) {
                    // <!-- 分字段，展示每个字段的变更 -->
                    html.append("<div style=\"display: flex; margin-bottom: 4px\">");
                    html.append("<div style=\"color: #86909c; margin-right: 8px\">")
                            .append(fieldChangeDTO.getFieldShowName() + "：")
                            .append("</div>");

                    if ("update".equals(label) || "delete".equals(label)) {
                        html.append("<span>");
                        html.append(fieldChangeDTO.getBeforeValue());
                        html.append("</span>");
                    }

                    if ("update".equals(label)) {
                        html.append("<icon-arrow-right><icon-arrow-right/>");
                    }

                    if (!"delete".equals(label)) {
                        html.append("<span>");
                        html.append(fieldChangeDTO.getAfterValue());
                        html.append("</span>");
                    }
                    html.append("</div>");
                }
                html.append("</div>");
                html.append("</div>");
            }
            html.append("</div>");
            html.append("</div>");
        }
    }

    private SysFieldDTO covertToSysFieldDTO(ModuleField field) {
        SysFieldDTO dto = new SysFieldDTO();
        dto.setFieldName(field.getTableName() + "." + field.getFieldCode());
        dto.setFieldShowName(field.getFieldName());
        return dto;
    }
}
