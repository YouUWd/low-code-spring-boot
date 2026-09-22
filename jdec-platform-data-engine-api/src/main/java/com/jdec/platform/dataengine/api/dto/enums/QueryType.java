package com.jdec.platform.dataengine.api.dto.enums;

/** 用于区分 查询类型 */
public enum QueryType {
    LIST,
    LIST_APPROVAL_TODO,
    LIST_APPROVAL_DONE,
    LIST_APPROVAL_ALL,
    DETAIL,
    DETAIL_APPROVAL_CURRENT,
    DETAIL_APPROVAL_HISTORY,
    DETAIL_SUBTABLE_QUERY,
    DETAIL_SUBMODULE_QUERY
}
