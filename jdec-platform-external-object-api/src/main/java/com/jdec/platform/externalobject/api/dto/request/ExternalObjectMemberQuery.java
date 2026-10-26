package com.jdec.platform.externalobject.api.dto.request;

import java.util.Collection;
import java.util.List;

/**
 * 外部对象人员查重查询条件。
 *
 * @param mobilePhoneNos 待查重的手机号
 * @param identityNos 待查重的证件号
 * @param subjectId 主体ID；为 null 时不按主体过滤
 * @param excludeCompanyId 需要排除的外部对象ID（变更申请单编辑时传自身ID）；为 null 时不排除
 * @param excludeBusinessNo 需要排除的外部对象业务编号（含历史版本）；为空时不排除
 */
public record ExternalObjectMemberQuery(
        Collection<String> mobilePhoneNos,
        Collection<String> identityNos,
        Long subjectId,
        Long excludeCompanyId,
        String excludeBusinessNo) {

    public ExternalObjectMemberQuery {
        mobilePhoneNos = mobilePhoneNos == null ? List.of() : List.copyOf(mobilePhoneNos);
        identityNos = identityNos == null ? List.of() : List.copyOf(identityNos);
    }
}
