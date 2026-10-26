package com.jdec.platform.externalobject.api.dto.request;

/**
 * 外部对象字段查重请求。
 *
 * <p>前端只需传入字段标识与该字段的值，查重范围由后端沿父子表关系解析。
 *
 * @param field 需要查重的字段，格式为 {@code table.field}（如 {@code member.mobile_phone_no}）
 * @param value 字段值
 */
public record ExternalObjectDuplicateCheckReq(String field, Object value) {}
