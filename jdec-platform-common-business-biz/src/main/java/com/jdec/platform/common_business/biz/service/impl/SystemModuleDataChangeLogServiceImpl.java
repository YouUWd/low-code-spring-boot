package com.jdec.platform.common_business.biz.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.common_business.api.dto.common.*;
import com.jdec.platform.common_business.biz.change.*;
import com.jdec.platform.common_business.biz.common.CommonConstant;
import com.jdec.platform.common_business.biz.common.DataSourceConstants;
import com.jdec.platform.common_business.biz.dto.BizLogDTO;
import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.common_business.biz.log.ComBizLogEventPublisher;
import com.jdec.platform.common_business.biz.mapper.TableChangeLogMapper;
import com.jdec.platform.common_business.biz.service.ConfigCenterApiService;
import com.jdec.platform.common_business.biz.service.SystemModuleDataChangeLogService;
import com.jdec.platform.common_business.biz.util.JsonUtil;
import com.jdec.platform.common_business.biz.util.LoginUserUtil;
import com.jdec.platform.shared.datasource.DataSource;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@DataSource(DataSourceConstants.STUDENT_MANAGE)
public class SystemModuleDataChangeLogServiceImpl implements SystemModuleDataChangeLogService {

    private final TableChangeLogMapper tableChangeLogMapper;

    private final ConfigCenterApiService configCenterApiService;

    @Override
    public Boolean saveLog(ModuleDataChangeContext context) {
        try {
            List<ModuleDataChangeDTO> modifyModules = compareOutChangeLog(context);
            if (CollectionUtil.isEmpty(modifyModules)) {
                // 没有任何变更记录,不用保存
                return null;
            }
            LinkedHashMap<Long, String> moduleNameMap = new LinkedHashMap<>();
            for (DataChangeContext dataChangeContext : context.getDataChangeContextList()) {
                moduleNameMap.put(
                        dataChangeContext.getModuleId(),
                        dataChangeContext.getModuleFilterInfo().getModuleInfo().getModuleName());
            }
            DataAuditContentDTO dataAuditContentDTO =
                    DataAuditContentDTO.builder().change(modifyModules).build();
            BizLogDTO bizLogDTO =
                    BizLogDTO.builder()
                            .logType(CommonConstant.MONITOR_LOG_OPERATOR_LOG_TYPE)
                            .projectNo(LoginUserUtil.getProjectNoFromHeader())
                            .token(LoginUserUtil.getTokenFromHeader())
                            .platform(LoginUserUtil.getOsFromHeader())
                            .browser(LoginUserUtil.getBrowserFromHeader())
                            .ip(LoginUserUtil.getIpFromHeader())
                            .origin(LoginUserUtil.getRefererFromHeader())
                            .loginUserId(LoginUserUtil.getRealLoginUserId())
                            .mockUserId(LoginUserUtil.getLoginUserId())
                            .subjectId(LoginUserUtil.getLongSubjectId())
                            .roleId(LoginUserUtil.getRoleId())
                            .logRemark(JsonUtil.obj2Json(dataAuditContentDTO))
                            .moduleId(context.getMainModuleId())
                            .moduleName(moduleNameMap.get(context.getMainModuleId()))
                            .dataId(
                                    context.getDataId() != null
                                            ? String.valueOf(context.getDataId())
                                            : null)
                            .menuId(context.getMenuId())
                            .build();
            if (context.getCallType() == CallTypeEnum.EDITE) {
                ComBizLogEventPublisher.publishLogEvent(BizLogEnum.DATA_UPDATE, bizLogDTO);
            }
            //            WechatTemplateDetailDTO wechatTemplateDetailDTO =
            //                    configCenterApiService.getWechatTemplateDetail(
            //                            LoginUserUtil.getLongSubjectId(), 6L);
            //            BizWechatMsgDTO bizWechatMsgDTO =
            //                    BizWechatMsgDTO.builder()
            //                            .param(new HashMap<>())
            //                            .wechatTemplate(wechatTemplateDetailDTO)
            //                            .token(LoginUserUtil.getTokenFromHeader())
            //                            .acceptorList(Lists.newArrayList(298L))
            //                            .subjectId(LoginUserUtil.getLongSubjectId())
            //                            .build();
            //            ComBizMsgEventPublisher.publishMsgEvent(BizMsgEnum.APPROVAL_CHAIN,
            // bizWechatMsgDTO);
            return Boolean.TRUE;
        } catch (Exception e) {
            log.error("save log error,context={},e=", context.toString(), e);
        }
        return Boolean.FALSE;
    }

    @Override
    public List<ModuleDataChangeDTO> compareOutChangeLog(ModuleDataChangeContext context) {
        if (context == null || CollectionUtil.isEmpty(context.getDataChangeContextList())) {
            return null;
        }
        LinkedHashMap<Long, List<DataChangeContext>> dataChangeMap = new LinkedHashMap<>();

        LinkedHashMap<Long, ModuleFilterInfo> moduleNameMap = new LinkedHashMap<>();
        for (DataChangeContext dataChangeContext : context.getDataChangeContextList()) {
            if (!dataChangeMap.containsKey(dataChangeContext.getModuleId())) {
                dataChangeMap.put(dataChangeContext.getModuleId(), new ArrayList<>());
            }
            dataChangeMap.get(dataChangeContext.getModuleId()).add(dataChangeContext);
            moduleNameMap.put(
                    dataChangeContext.getModuleId(), dataChangeContext.getModuleFilterInfo());
        }
        // OperationChangeDTO operationChangeDTO = new OperationChangeDTO();

        List<ModuleDataChangeDTO> modifyModules = new ArrayList<>();

        for (Map.Entry<Long, List<DataChangeContext>> entry : dataChangeMap.entrySet()) {
            Long moduleId = entry.getKey();
            ModuleDataChangeDTO moduleDataChangeDTO = new ModuleDataChangeDTO();
            moduleDataChangeDTO.setMId(moduleId);
            ModuleFilterInfo moduleFilterInfo = moduleNameMap.get(moduleId);
            moduleDataChangeDTO.setName(moduleFilterInfo.getModuleInfo().getModuleName());

            // 获取模块 的唯一标识字段
            List<ModuleField> fields =
                    moduleFilterInfo.getAllFields().stream()
                            .filter(x -> (x.getBizKeyOrder() != null && x.getBizKeyOrder() > 0))
                            .collect(Collectors.toList());
            Map<String, List<String>> tableUniqueFieldMap = new HashMap<>();
            if (CollectionUtil.isNotEmpty(fields)) {
                fields.sort(Comparator.comparing(ModuleField::getBizKeyOrder));
                for (ModuleField f : fields) {
                    if (!tableUniqueFieldMap.containsKey(f.getTableName())) {
                        tableUniqueFieldMap.put(f.getTableName(), new ArrayList<>());
                    }
                    tableUniqueFieldMap.get(f.getTableName()).add(f.getFieldCode());
                }
            }

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
                if (dataChangeContext.getBefore() == null && dataChangeContext.getAfter() != null) {
                    RowChangeDTO rowChangeDTO =
                            TableUtils.getInsertOrDeleteRowChange(
                                    dataChangeContext.getAfter(),
                                    sysFieldMap,
                                    tableUniqueFieldMap,
                                    true);
                    if (rowChangeDTO != null
                            && CollectionUtil.isNotEmpty(rowChangeDTO.getColumns())) {
                        insertRows.add(rowChangeDTO);
                    }

                } else if (dataChangeContext.getBefore() != null
                        && dataChangeContext.getAfter() != null) {
                    // 更新操作
                    RowChangeDTO rowChangeDTO =
                            TableUtils.getUpdateFieldChangeRow(
                                    dataChangeContext.getBefore(),
                                    dataChangeContext.getAfter(),
                                    sysFieldMap,
                                    tableUniqueFieldMap);
                    if (rowChangeDTO != null
                            && CollectionUtil.isNotEmpty(rowChangeDTO.getColumns())) {
                        updateRows.add(rowChangeDTO);
                    }
                } else {
                    // 删除操作
                    RowChangeDTO rowChangeDTO =
                            TableUtils.getInsertOrDeleteRowChange(
                                    dataChangeContext.getBefore(),
                                    sysFieldMap,
                                    tableUniqueFieldMap,
                                    false);
                    if (rowChangeDTO != null
                            && CollectionUtil.isNotEmpty(rowChangeDTO.getColumns())) {
                        deleteRows.add(rowChangeDTO);
                    }
                }
            }

            if (CollectionUtil.isNotEmpty(insertRows)) {
                dataChangeActionDTO.setI(insertRows);
            }

            if (CollectionUtil.isNotEmpty(updateRows)) {
                dataChangeActionDTO.setU(updateRows);
            }

            if (CollectionUtil.isNotEmpty(deleteRows)) {
                dataChangeActionDTO.setD(deleteRows);
            }

            if (CollectionUtil.isNotEmpty(dataChangeActionDTO.getI())
                    || CollectionUtil.isNotEmpty(dataChangeActionDTO.getU())
                    || CollectionUtil.isNotEmpty(dataChangeActionDTO.getD())) {
                moduleDataChangeDTO.setActions(dataChangeActionDTO);
                modifyModules.add(moduleDataChangeDTO);
            }
        }
        return modifyModules;
    }

    //    @Override
    //    public String getLogContentHtml(OperationChangeDTO operationChangeDTO) {
    //        if (CollectionUtil.isEmpty(operationChangeDTO.getModifyModules())) {
    //            return null;
    //        }
    //        StringBuilder html = new StringBuilder();
    //        for (ModuleDataChangeDTO moduleDataChangeDTO : operationChangeDTO.getModifyModules())
    // {
    //            html.append(
    //                    "<div style=\"display: flex; align-items: flex-start; margin-bottom: 8px;
    // color: #1d2129\">");
    //            html.append(
    //                    "<div style=\"background-color: #e5e6eb; width: 64px; padding: 2px 0;
    // margin-right: 8px; text-align: center\">");
    //            html.append(moduleDataChangeDTO.getName());
    //            html.append("</div>");
    //            appendActionHtml(moduleDataChangeDTO.getActions().getA(), html, "insert");
    //            appendActionHtml(moduleDataChangeDTO.getActions().getU(), html, "update");
    //            appendActionHtml(moduleDataChangeDTO.getActions().getD(), html, "delete");
    //            html.append("</div>");
    //        }
    //        return html.toString();
    //    }
    //
    //    private void appendActionHtml(List<RowChangeDTO> rows, StringBuilder html, String label) {
    //        if (CollectionUtil.isNotEmpty(rows)) {
    //            html.append("<div>");
    //            html.append("<div style=\"display: flex; margin-bottom: 8px\">");
    //            html.append("<div style=\"margin-right: 10px\">");
    //            if ("insert".equals(label)) {
    //                html.append("增加");
    //            } else if ("update".equals(label)) {
    //                html.append("修改");
    //            } else {
    //                html.append("删除");
    //            }
    //            html.append("</div>");
    //            for (RowChangeDTO rowChangeDTO : rows) {
    //                html.append("<div>");
    //                html.append("<div style=\"display: flex\">");
    //                if (StringUtils.isNotEmpty(rowChangeDTO.getName())) {
    //                    html.append(" <div style=\"margin-right: 10px\">")
    //                            .append(rowChangeDTO.getName())
    //                            .append("</div>");
    //                }
    //                html.append("<div>");
    //                for (com.jdec.platform.common_business.api.dto.common.FieldChangeDTO
    //                        fieldChangeDTO : rowChangeDTO.getColumns()) {
    //                    // <!-- 分字段，展示每个字段的变更 -->
    //                    html.append("<div style=\"display: flex; margin-bottom: 4px\">");
    //                    html.append("<div style=\"color: #86909c; margin-right: 8px\">")
    //                            .append(fieldChangeDTO.getName() + "：")
    //                            .append("</div>");
    //
    //                    if ("update".equals(label) || "delete".equals(label)) {
    //                        html.append("<span>");
    //                        html.append(fieldChangeDTO.getOld());
    //                        html.append("</span>");
    //                    }
    //
    //                    if ("update".equals(label)) {
    //                        html.append("<icon-arrow-right><icon-arrow-right/>");
    //                    }
    //
    //                    if (!"delete".equals(label)) {
    //                        html.append("<span>");
    //                        html.append(fieldChangeDTO.getNewer());
    //                        html.append("</span>");
    //                    }
    //                    html.append("</div>");
    //                }
    //                html.append("</div>");
    //                html.append("</div>");
    //            }
    //            html.append("</div>");
    //            html.append("</div>");
    //        }
    //    }

    private SysFieldDTO covertToSysFieldDTO(ModuleField field) {
        SysFieldDTO dto = new SysFieldDTO();
        dto.setFieldName(field.getTableName() + "." + field.getFieldCode());
        dto.setFieldShowName(field.getFieldName());
        return dto;
    }
}
