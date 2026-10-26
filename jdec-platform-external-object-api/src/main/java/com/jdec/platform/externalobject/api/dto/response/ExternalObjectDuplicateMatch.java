package com.jdec.platform.externalobject.api.dto.response;

import java.util.Map;

/**
 * 查重命中的已保存记录。
 *
 * @param companyId 命中记录所属的外部对象主单ID
 * @param companyName 命中记录所属的外部对象全称
 * @param record 命中记录（字段名 -> 字段值），人员查重时前端可用于展示已存在的人员信息
 */
public record ExternalObjectDuplicateMatch(
        Long companyId, String companyName, Map<String, Object> record) {}
