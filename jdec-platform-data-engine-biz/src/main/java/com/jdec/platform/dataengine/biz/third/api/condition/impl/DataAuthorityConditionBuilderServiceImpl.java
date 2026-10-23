package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.config.api.dto.response.DataRightConfigResp;
import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import com.jdec.platform.shared.context.UserContext;
import com.jdec.platform.shared.utils.JsonValueUtils;
import java.util.*;
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

    private final Set<QueryType> TYPE_SET =
            Set.of(
                    QueryType.LIST,
                    QueryType.DETAIL,
                    QueryType.DETAIL_APPROVAL_CURRENT,
                    QueryType.DETAIL_SUBMODULE_QUERY);

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return false;
        //        return TYPE_SET.contains(queryType);
    }

    @Override
    public Condition buildCondition(StatementContext context) {
        // 查询数据权限配置

        List<DataRightConfigResp> dataRightconfigs = context.getDataRightConfigResps();

        if (CollectionUtils.isEmpty(dataRightconfigs)) {
            log.debug("数据权限设置为空，无数据查看权限。");
            return DSL.trueCondition();
        }

        List<Condition> conditions = new ArrayList<>();

        Map<String, List<DataRightConfigResp>> tableNameToConfigItems =
                dataRightconfigs.stream()
                        .collect(Collectors.groupingBy(DataRightConfigResp::getTableName));

        // 主表数据权限筛选条件构造
        applyDataRightFilter(context.getTableName(), tableNameToConfigItems, conditions);

        // 顶层模块查询增加创建人筛选条件OR
        if (context.getQueryType() != QueryType.DETAIL_SUBMODULE_QUERY) {
            Long userId =
                    Objects.nonNull(UserContext.getMockUserId())
                            ? UserContext.getMockUserId()
                            : UserContext.getRealUserId();

            Condition createdByCondition =
                    DSL.field(DSL.name(context.getTableName(), "created_by")).eq(userId);

            return DSL.and(conditions).or(createdByCondition);
        } else {
            return DSL.and(conditions);
        }
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
                        if ((!Integer.valueOf(1).equals(dataConfig.getPermissionCheckAll()))) {
                            log.debug(
                                    "表名:{}字段{}的数据权限生效，值列表:{}",
                                    tableName,
                                    dataConfig.getColumnName(),
                                    dataConfig.getBizNames());

                            Long subjectId = dataConfig.getSubjectId();
                            Integer subjectCheckAll = dataConfig.getSubjectCheckAll();
                            String columnName = dataConfig.getColumnName();
                            List<Object> bizNames = dataConfig.getBizNames();

                            if (subjectId != null) {
                                log.debug("主体不为空，根据主体ID:{}进行数据权限筛选", subjectId);
                                conditions.add(
                                        DSL.field(DSL.name(tableName, "subject_id")).eq(subjectId));
                            }

                            if (!Integer.valueOf(1).equals(subjectCheckAll)) {
                                log.debug("主体数据权限非全选，进行数据权限筛选。");
                                if (!CollectionUtils.isEmpty(bizNames)) {
                                    log.debug("勾选项不为空，进行数据权限筛选。");
                                    List<Object> selectableValues = new ArrayList<>(bizNames);
                                    boolean includeNull = selectableValues.remove("Null");
                                    if (!includeNull) {
                                        conditions.add(
                                                DSL.field(DSL.name(tableName, columnName))
                                                        .in(toSqlValues(selectableValues)));
                                    } else {
                                        log.debug("筛选条件包含空值");
                                        if (selectableValues.isEmpty()) {
                                            log.debug("筛选为Null的数据");
                                            conditions.add(
                                                    DSL.field(DSL.name(tableName, columnName))
                                                            .isNull());
                                        } else {
                                            conditions.add(
                                                    DSL.field(DSL.name(tableName, columnName))
                                                            .isNull()
                                                            .or(
                                                                    DSL.field(
                                                                                    DSL.name(
                                                                                            tableName,
                                                                                            columnName))
                                                                            .in(
                                                                                    toSqlValues(
                                                                                            selectableValues))));
                                        }
                                    }
                                } else {
                                    log.debug("数据权限勾选项未空，不返回数据。");
                                    conditions.add(DSL.falseCondition());
                                }
                            }
                        } else {
                            log.debug("数据权限全选，数据表{}不进行数据权限控制", tableName);
                        }
                    }
                }
            }
        }
    }

    /** 业务值为 JSON 对象/数组时转为 JSON 文本参与比较，避免把集合直接绑定给 JDBC */
    private List<Object> toSqlValues(List<Object> values) {
        return values.stream().map(JsonValueUtils::toSqlValue).collect(Collectors.toList());
    }
}
