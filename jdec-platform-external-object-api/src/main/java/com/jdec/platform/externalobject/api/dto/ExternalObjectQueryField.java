package com.jdec.platform.externalobject.api.dto;

/** 参与查重的外部对象字段。 */
public enum ExternalObjectQueryField {

    /** 外部对象全称（info.company_name） */
    COMPANY_NAME,

    /** 统一社会信用代码（basic_info.uscc） */
    USCC,

    /** 外部对象简称（basic_info.short_name） */
    SHORT_NAME,

    /** 外部对象英文全称（basic_info.en_full_name） */
    EN_FULL_NAME,

    /** 外部对象英文简称（basic_info.en_short_name） */
    EN_SHORT_NAME
}
