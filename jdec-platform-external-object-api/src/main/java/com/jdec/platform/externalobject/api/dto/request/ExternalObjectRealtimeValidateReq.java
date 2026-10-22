package com.jdec.platform.externalobject.api.dto.request;

/**
 * 外部对象字段实时校验请求（不含查重）。
 *
 * <p>前端在表单字段编辑后实时调用，只传当前字段与值；关联字段（如 {@code info.nature}、人员其它字段） 由后端按 {@code companyId}/{@code
 * subId} 实时查询数据库获取。
 *
 * @param companyId 外部对象主单ID（info.id）
 * @param subId 当前编辑记录ID（info 表为 info.id，子表为子表行ID）；新增时可为空
 * @param field 需要校验的字段，格式为 {@code table.field}（如 {@code info.reg_status}）
 * @param value 字段值；来自词云的字段为 JSON 字符串或 JSON 结构
 */
public record ExternalObjectRealtimeValidateReq(
        Long companyId, Long subId, String field, Object value) {}
