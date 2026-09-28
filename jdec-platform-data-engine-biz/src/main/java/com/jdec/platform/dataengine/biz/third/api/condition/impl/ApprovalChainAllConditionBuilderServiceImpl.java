package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.approval.api.IApprovalEngine;
import com.jdec.platform.approval.api.req.ApprovalListReq;
import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.response.DataRightConfigResp;
import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import com.jdec.platform.shared.context.AppContext;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 审批链 构建 全部 查询条件 筛选创建人、审批链参与、数据权限、草稿数据 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalChainAllConditionBuilderServiceImpl
        implements SingleBizConditionBuilderService {

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.LIST_APPROVAL_ALL);

    private final IApprovalEngine approvalEngine;

    private final SysRolePermissionApi sysRolePermissionApi;

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public Condition buildCondition(StatementContext context) {

        String primaryTableName =
                null; // context.getSysModuleCompleteResp().getModule().getPrimaryTable();

        Condition condition = DSL.field(DSL.name(primaryTableName, "deleted")).eq(0);

        // 创建人
        Condition orConditions =
                DSL.field(DSL.name(primaryTableName, "create_by")).eq(AppContext.getUserId());

        // 审批链参与人
        List<Long> toDoIdList = getToDoIdList(context);

        if (!CollectionUtils.isEmpty(toDoIdList)) {
            orConditions.or(DSL.field(DSL.name(primaryTableName, "id")).in(toDoIdList));
        }

        // 数据权限
        orConditions.or(buildDataRightCondition(context));

        condition.and(orConditions);

        return condition;
    }

    /**
     * 获取列表中待办的数据ID列表
     *
     * @param context
     * @return
     */
    private List<Long> getToDoIdList(StatementContext context) {
        ApprovalListReq approvalListReq = new ApprovalListReq();
        approvalListReq.setApprovalType(3);
        approvalListReq.setRoleId(AppContext.getRoleId());

        // TODO 数据来源于其他主体时需要拿到对应主体的详情模块ID
        approvalListReq.setModuleIdList(Arrays.asList(context.getModuleId()));

        approvalListReq.setCurrentUserId(AppContext.getUserId());
        approvalListReq.setApplicantId(AppContext.getUserId());

        //        approvalListReq.setSubjectIdList(
        //                CollectionUtils.isEmpty(
        //
        // context.getSysModuleCompleteResp().getModule().getSourceSubjects())
        //                        ? Arrays.asList(AppContext.getSubjectId())
        //                        : context
        //                                .getSysModuleCompleteResp()
        //                                .getModule()
        //                                .getSourceSubjects()
        //                                .stream()
        //                                .map(SysModuleResp.SubjectInfo::getId)
        //                                .collect(Collectors.toList()));

        List<Map<String, Object>> approvalListResult =
                approvalEngine.getBusinessList(approvalListReq);

        context.setApprovalList(approvalListResult);

        List<Long> toDoIdList = new ArrayList<>();

        // 构造recordId为key,审批数据为值的映射map，方便后面数据拼装
        for (Map<String, Object> item : approvalListResult) {
            if (item.containsKey("recordId")) {
                toDoIdList.add(Long.parseLong(String.valueOf(item.get("recordId"))));
            }
        }

        return toDoIdList;
    }

    private Condition buildDataRightCondition(StatementContext context) {
        // 查询数据权限配置

        List<DataRightConfigResp> dataRightconfigs =
                sysRolePermissionApi.getRoleDataPermissionDetail(AppContext.getRoleId());

        if (CollectionUtils.isEmpty(dataRightconfigs)) {
            log.debug("数据权限设置为空，无数据查看权限。");
            return DSL.falseCondition();
        }

        List<Condition> conditions = new ArrayList<>();

        Map<String, List<DataRightConfigResp>> tableNameToConfigItems =
                dataRightconfigs.stream()
                        .collect(Collectors.groupingBy(DataRightConfigResp::getTableName));

        // 主表数据权限筛选条件构造
        //        applyDataRightFilter(
        //                context.getSysModuleCompleteResp().getModule().getPrimaryTable(),
        //                tableNameToConfigItems,
        //                conditions);

        // 遍历关联表，构造关联表查询条件
        //        for (ModuleTableDTO moduleTable :
        // context.getSysModuleCompleteResp().getModuleTables()) {
        //            applyDataRightFilter(moduleTable.getTableName(), tableNameToConfigItems,
        // conditions);
        //        }

        return DSL.and(conditions);
    }

    /**
     * 数据权限筛选条件构造方法
     *
     * @param tableName 表名
     * @param tableDataRightConf 表名对应的列数据权限配置
     * @param conditions 筛选条件列表
     */
    private void applyDataRightFilter(
            String tableName,
            Map<String, List<DataRightConfigResp>> tableDataRightConf,
            List<Condition> conditions) {
        // 表有配置数据权限字段
        if (tableDataRightConf.containsKey(tableName)) {
            if (!CollectionUtils.isEmpty(tableDataRightConf.get(tableName))) {
                DataRightConfigResp dataRightConfigResp = tableDataRightConf.get(tableName).get(0);
                if (!CollectionUtils.isEmpty(dataRightConfigResp.getDataList())) {
                    for (DataRightConfigResp.DataConfig dataConfig :
                            dataRightConfigResp.getDataList()) {
                        if (!CollectionUtils.isEmpty(dataConfig.getBizNames())) {
                            log.debug(
                                    "表名:{}字段{}的数据权限生效，值列表:{}",
                                    tableName,
                                    dataConfig.getColumnName(),
                                    dataConfig.getBizNames());
                            boolean includeNull = dataConfig.getBizNames().contains("Null");
                            if (!includeNull) {
                                conditions.add(
                                        DSL.field(DSL.name(tableName, dataConfig.getColumnName()))
                                                .in(dataConfig.getBizNames()));
                            } else {
                                log.debug("筛选条件包含空值");
                                dataConfig.getBizNames().remove("Null");

                                if (CollectionUtils.isEmpty(dataConfig.getBizNames())) {
                                    log.debug("筛选为Null的数据");
                                    conditions.add(
                                            DSL.field(
                                                            DSL.name(
                                                                    tableName,
                                                                    dataConfig.getColumnName()))
                                                    .isNull());
                                } else {
                                    conditions.add(
                                            DSL.field(
                                                            DSL.name(
                                                                    tableName,
                                                                    dataConfig.getColumnName()))
                                                    .isNull()
                                                    .or(
                                                            DSL.field(
                                                                            DSL.name(
                                                                                    tableName,
                                                                                    dataConfig
                                                                                            .getColumnName()))
                                                                    .in(dataConfig.getBizNames())));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
