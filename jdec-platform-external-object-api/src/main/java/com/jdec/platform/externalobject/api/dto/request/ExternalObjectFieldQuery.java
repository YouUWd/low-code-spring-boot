package com.jdec.platform.externalobject.api.dto.request;

import com.jdec.platform.externalobject.api.dto.ExternalObjectQueryField;
import java.util.Collection;
import java.util.List;

/**
 * 外部对象字段查重查询条件。
 *
 * @param field 查重字段
 * @param values 待查重的字段值
 * @param subjectId 主体ID；为 null 时不按主体过滤
 * @param excludeCompanyId 需要排除的外部对象ID（变更申请单编辑时传自身ID）；为 null 时不排除
 * @param excludeBusinessNo 需要排除的外部对象业务编号（含历史版本）；为空时不排除
 */
public record ExternalObjectFieldQuery(
        ExternalObjectQueryField field,
        Collection<String> values,
        Long subjectId,
        Long excludeCompanyId,
        String excludeBusinessNo) {

    public ExternalObjectFieldQuery {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
