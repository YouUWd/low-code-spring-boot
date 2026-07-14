package com.jdec.platform.common_business.biz.enums;

import lombok.Getter;

@Getter
public enum QueryOpEnum {
    OP_IN("in"),
    OP_LIKE("like"),
    OP_EQ("eq"),
    OP_ORIGINAL_DATE_RANGE("originalDateRange"),
    OP_DATE_RANGE("dateRange"),
    OP_DATE_OVERLAP_RANGE("dateOverlapRange"),
    OP_BETWEEN("between"),
    OP_TEXT_CONTAIN("textContain");

    private QueryOpEnum(String op) {
        this.op = op;
    }

    private String op;

    public static QueryOpEnum getQueryOpEnumByOp(String op) {
        for (QueryOpEnum q : QueryOpEnum.values()) {
            if (q.op.equals(op)) {
                return q;
            }
        }
        return null;
    }
}
