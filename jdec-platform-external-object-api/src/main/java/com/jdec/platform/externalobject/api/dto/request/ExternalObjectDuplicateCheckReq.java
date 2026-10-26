package com.jdec.platform.externalobject.api.dto.request;

/**
 * 外部对象字段查重请求。
 *
 * <p>前端只需传入字段标识与该字段的值，查重范围由后端沿父子表关系解析。
 *
 * <p>人员字段（{@code member.name}、{@code member.mobile_phone_no}、{@code
 * member.identity_no}）按姓名、手机号、证件号三者组合判断，不是单字段查重：其余两个字段的值与自身记录由后端按 {@code subId}
 * 查询人员行补齐并排除，因此编辑已保存的人员行时需要传入 {@code subId}（新增行可不传）。
 *
 * @param companyId 外部对象主单ID（info.id）；为空时按 {@code subId} 反查所属主单
 * @param subId 当前编辑的人员行ID（member.id）；新增行或主表字段可为空
 * @param field 需要查重的字段，格式为 {@code table.field}（如 {@code member.mobile_phone_no}）
 * @param value 字段值
 */
public record ExternalObjectDuplicateCheckReq(
        Long companyId, Long subId, String field, Object value) {}
