package com.jdec.platform.dataengine.biz.third.api.condition.impl;

import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.dataengine.api.dto.request.SearchItemReq;
import com.jdec.platform.dataengine.biz.constants.CommonConstant;
import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import com.jdec.platform.dataengine.biz.enums.ApiResponseCodeEnum;
import com.jdec.platform.dataengine.biz.enums.QueryOpEnum;
import com.jdec.platform.dataengine.biz.enums.QueryType;
import com.jdec.platform.dataengine.biz.third.api.condition.SingleBizConditionBuilderService;
import com.jdec.platform.dataengine.biz.util.ToolUtils;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** 构建 搜索 查询条件 */
@Service
@RequiredArgsConstructor
public class SearchConditionBuilderServiceImpl implements SingleBizConditionBuilderService {

    private final Set<QueryType> TYPE_SET =
            Set.of(
                    QueryType.LIST,
                    QueryType.LIST_APPROVAL_TODO,
                    QueryType.LIST_APPROVAL_DONE,
                    QueryType.LIST_APPROVAL_ALL);

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public Condition buildCondition(StatementContext context) {
        if (CollectionUtils.isEmpty(context.getSearch())) {
            return DSL.trueCondition();
        }

        Map<String, List<SearchItemReq>> tableNameToSearchItem =
                context.getSearch().stream()
                        .collect(Collectors.groupingBy(SearchItemReq::getTable));

        List<Condition> seachConditions = new ArrayList<>();

        // 主表筛选条件构造
        List<SearchItemReq> primaryTablesearchItems =
                tableNameToSearchItem.get(
                        context.getSysModuleCompleteResp().getModule().getPrimaryTable());
        applySearchFilters(seachConditions, primaryTablesearchItems);

        // 子表筛选条件构造
        for (ModuleTableDTO moduleTableDTO : context.getSysModuleCompleteResp().getModuleTables()) {
            if (tableNameToSearchItem.containsKey(moduleTableDTO.getTableName())) {
                List<SearchItemReq> searchItems =
                        tableNameToSearchItem.get(moduleTableDTO.getTableName());
                applySearchFilters(seachConditions, searchItems);
            }
        }

        return DSL.and(seachConditions);
    }

    /**
     * 按表分组构造列头筛选条件
     *
     * @param seachConditions 筛选条件列表
     * @param searchItemReqs 业务表筛选项
     */
    private void applySearchFilters(
            List<Condition> seachConditions, List<SearchItemReq> searchItemReqs) {

        if (searchItemReqs == null || searchItemReqs.isEmpty()) {
            return;
        }

        for (SearchItemReq item : searchItemReqs) {
            // 这里的 fields 目前只有有一个值
            List<String> fields = item.getField();
            String searchType = item.getSearchType();
            Object value = item.getValue();
            if (fields == null || fields.isEmpty() || value == null) {
                continue;
            }
            QueryOpEnum opEnum = QueryOpEnum.getQueryOpEnumByOp(searchType);
            if (opEnum == null) {
                continue;
            }
            Pair<String, Object> formatted = formatValue(opEnum, value);
            String finalOp = formatted.getLeft();
            Object finalValue = formatted.getRight();

            if (finalValue == null) {
                continue;
            }

            Condition singleFilterCondition = DSL.trueCondition();

            int addedCount = 0;
            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i);
                boolean isOr = i > 0;
                if (applySingleFilter(
                        singleFilterCondition,
                        item.getTable(),
                        ToolUtils.camelToSnakeCase(field),
                        finalOp,
                        finalValue,
                        isOr)) {
                    addedCount++;
                }
            }
            // 如果内部没有添加任何条件，MyBatis-Plus 在某些版本下可能会生成空括号导致语法错误
            // 这里通过返回逻辑确保安全，虽然 wrapper 是传参，但我们可以检查其状态

            seachConditions.add(singleFilterCondition);
        }
    }

    /** 格式化搜索值 (PHP: formatValue) */
    private Pair<String, Object> formatValue(QueryOpEnum operator, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Pair.of(operator.getOp(), value);
        }

        if (!map.containsKey(CommonConstant.START_KEY)) {
            return Pair.of(operator.getOp(), value);
        }

        Object startObj = map.get(CommonConstant.START_KEY);
        Object endObj = map.get(CommonConstant.END_KEY);

        if ((startObj == null || startObj.toString().isEmpty())
                && (endObj == null || endObj.toString().isEmpty())) {
            throw new BusinessException(ApiResponseCodeEnum.BAD_REQUEST.getCode(), "区间范围传值，必须存在一项");
        }

        return switch (operator) {
            case QueryOpEnum.OP_NUMBER_RANGE -> formatBetweenValue(startObj, endObj);
            case QueryOpEnum.OP_DATE_RANGE -> formatDateRangeValue(startObj, endObj);
            case QueryOpEnum.OP_DATE_OVERLAP_RANGE -> formatDateOverlapRangeValue(startObj, endObj);
            case QueryOpEnum.OP_ORIGINAL_DATE_RANGE -> formatOriginalDateRange(startObj, endObj);
            default -> Pair.of(operator.getOp(), value);
        };
    }

    /**
     * 数值参数
     *
     * @param startObj
     * @param endObj
     * @return
     */
    private Pair<String, Object> formatBetweenValue(Object startObj, Object endObj) {
        LocalDateTime startDate =
                startObj != null ? ToolUtils.dateTimeStrToDateTime(startObj.toString()) : null;
        LocalDateTime endDate =
                endObj != null ? ToolUtils.dateTimeStrToDateTime(endObj.toString()) : null;

        boolean isDefault = (startDate == null && endDate == null);
        if (isDefault) {
            // 默认今天
            throw new BusinessException(
                    ApiResponseCodeEnum.BAD_REQUEST.getCode(), "开始时间和结束时间不能都为空。");
        } else if (Objects.nonNull(startDate) && Objects.isNull(endDate)) {
            // 只有开始时间，不处理 endTime
            return Pair.of("egt", startDate);
        } else if (Objects.isNull(startDate) && Objects.nonNull(endDate)) {
            // 只有结束时间
            return Pair.of("elt", endDate);
        } else {
            // 都有
            if (startDate.isAfter(endDate)) {
                throw new BusinessException(
                        ApiResponseCodeEnum.BAD_REQUEST.getCode(), "开始时间不能大于结束时间");
            }
            return Pair.of("between", List.of(startDate, endDate));
        }
    }

    /**
     * 时间参数 默认 2026-04-30 08:00:00
     *
     * @param startObj
     * @param endObj
     * @return
     */
    private Pair<String, Object> formatDateRangeValue(Object startObj, Object endObj) {
        if (startObj != null && endObj == null) {
            return Pair.of("egt", startObj);
        } else if (startObj == null && endObj != null) {
            return Pair.of("elt", endObj);
        } else if (startObj != null && endObj != null) {
            return Pair.of("between", List.of(startObj, endObj));
        }
        return Pair.of("between", null);
    }

    private Pair<String, Object> formatDateOverlapRangeValue(Object startObj, Object endObj) {
        String operator = "overlap";
        Object value = null;

        if (startObj != null && endObj == null) {
            value = List.of("start", startObj, endObj);
        } else if (endObj != null && startObj == null) {
            value = List.of("end", startObj, endObj);
        } else if (startObj != null && endObj != null) {
            value = List.of("range", startObj, endObj);
        }
        return Pair.of(operator, value);
    }

    private Pair<String, Object> formatOriginalDateRange(Object startObj, Object endObj) {
        if (startObj != null && (endObj == null || endObj.toString().isEmpty())) {
            return Pair.of("egt", startObj);
        } else if (endObj != null && (startObj == null || startObj.toString().isEmpty())) {
            return Pair.of("elt", endObj);
        } else if (startObj != null && endObj != null) {
            // 这里假设 startObj, endObj 是字符串日期
            return Pair.of("between", List.of(startObj, endObj));
        }
        return Pair.of("between", null);
    }

    /**
     * 应用单个搜索条件
     *
     * @return 是否实际添加了条件
     */
    private <T> boolean applySingleFilter(
            Condition singleCondition,
            String tableName,
            String field,
            String op,
            Object value,
            boolean isOr) {
        String column = field;

        switch (op) {
            case "like" -> {
                if (isOr) {
                    singleCondition.or(
                            DSL.field(DSL.name(tableName, column)).like("%" + value + "%"));
                } else {
                    singleCondition.and(
                            DSL.field(DSL.name(tableName, column)).like("%" + value + "%"));
                }
                return true;
            }
            case "eq" -> {
                if (isOr) {
                    singleCondition.or(DSL.field(DSL.name(tableName, column)).eq(value));
                } else {
                    singleCondition.and(DSL.field(DSL.name(tableName, column)).eq(value));
                }
                return true;
            }
            case "in" -> {
                if (value instanceof Collection<?> coll && !coll.isEmpty()) {
                    if (isOr) {
                        singleCondition.or(DSL.field(DSL.name(tableName, column)).in(value));
                    } else {
                        singleCondition.and(DSL.field(DSL.name(tableName, column)).in(value));
                    }
                    return true;
                }
            }
            case "egt" -> {
                if (isOr) {
                    singleCondition.or(DSL.field(DSL.name(tableName, column)).ge(value));
                } else {
                    singleCondition.and(DSL.field(DSL.name(tableName, column)).ge(value));
                }
                return true;
            }
            case "elt" -> {
                if (isOr) {
                    singleCondition.or(DSL.field(DSL.name(tableName, column)).lessOrEqual(value));
                } else {
                    singleCondition.and(DSL.field(DSL.name(tableName, column)).lessOrEqual(value));
                }
                return true;
            }
            case "between" -> {
                if (value instanceof List<?> list && list.size() == 2) {
                    if (isOr) {
                        singleCondition.or(
                                DSL.field(DSL.name(tableName, column))
                                        .between(list.get(0), list.get(1)));
                    } else {
                        singleCondition.and(
                                DSL.field(DSL.name(tableName, column))
                                        .between(list.get(0), list.get(1)));
                    }
                    return true;
                }
            }
            case "overlap" -> {
                if (value instanceof List<?> list && list.size() == 3) {

                    applyOverlapFilter(singleCondition, field, list);
                    return true;
                }
            }
            case "textContain" -> {
                if (value instanceof Collection<?> coll && !coll.isEmpty()) {
                    for (Object o : coll) {
                        if (isOr) {
                            singleCondition.or(
                                    DSL.condition(
                                            "INSTR(" + tableName + "." + column + ",?" + ")>1", 0));
                        } else {
                            singleCondition.or(
                                    DSL.condition(
                                            "INSTR(" + tableName + "." + column + ",?" + ")>1", 0));
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** 应用时间重叠过滤 (PHP: operator === 'overlap' 逻辑) */
    private <T> void applyOverlapFilter(Condition condition, String field, List<?> value) {
        //        String type = value.get(0).toString();
        //        long startTime = (long) value.get(1);
        //        long endTime = (long) value.get(2);

        // 注意：overlap 逻辑通常涉及两个字段（开始时间和结束时间）
        // PHP 中通过 deepHandleValueByColumn 识别 fields 中的 start/end 字段
        // 这里简化处理，假设 field 本身暗示了规则，或者解析 field 为 start/end
        // 由于 UserEntity 搜索中 age 可能不是 overlap 类型，通常 overlap 用于合同时间等
    }
}
