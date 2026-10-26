package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum QueryOpEnum {
    OP_SINGLE_FUZZY_SELECT("singleFuzzySelect", "like"),
    OP_TEXT("text", "like"),
    OP_GENERAL_TREE("generalTree", "in"),
    OP_USER_TREE("userTree", "in"),
    OP_MULTIPLE_SELECT("multipleSelect", "in"),
    OP_USER_MULTI_SELECT("userMultiSelect", "in"),
    OP_NUMBER_RANGE("numberRange", "between"),
    OP_SINGLE_SELECT("singleSelect", "eq"),
    OP_DATE_RANGE("dateRange", "dateRange"),
    OP_DATE_OVERLAP_RANGE("dateOverlapRange", "dateOverlapRange"),
    OP_ORIGINAL_DATE_RANGE("originalDateRange", "originalDateRange");

    QueryOpEnum(String searchType, String op) {
        this.searchType = searchType;
        this.op = op;
    }

    private final String searchType;

    private final String op;

    public static QueryOpEnum getQueryOpEnumByOp(String searchType) {
        for (QueryOpEnum q : QueryOpEnum.values()) {
            if (q.searchType.equals(searchType)) {
                return q;
            }
        }
        return null;
    }
}
