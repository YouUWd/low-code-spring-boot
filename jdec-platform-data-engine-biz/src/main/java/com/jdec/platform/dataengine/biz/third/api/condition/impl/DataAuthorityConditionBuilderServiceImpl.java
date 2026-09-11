package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.config.api.SysRolePermissionApi;
import com.jdec.platform.config.api.dto.response.DataRightConfigResp;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import com.jdec.platform.shared.context.AppContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 构建 数据 权限查询条件 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAuthorityConditionBuilderServiceImpl implements SingleBizConditionBuilderService {

    private final Set<QueryType> TYPE_SET = Set.of(QueryType.LIST);

    private final SysRolePermissionApi sysRolePermissionApi;

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public Condition buildCondition(StatementContext context) {
        // 查询数据权限配置

        List<DataRightConfigResp> dataRightconfigs =
                sysRolePermissionApi.getRoleDataPermissionDetail(AppContext.getRoleId());

        if (CollectionUtils.isEmpty(dataRightconfigs)) {
            log.debug("数据权限设置为空，无数据查看权限。");
            return DSL.trueCondition();
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
