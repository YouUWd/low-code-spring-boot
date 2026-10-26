package com.jdec.platform.externalobject.api;

import com.jdec.platform.externalobject.api.dto.request.ExternalObjectDuplicateCheckReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectDuplicateCheckResp;

/**
 * 外部对象字段查重接口。
 *
 * <p>前端只需传入 {@code table.field} 与字段值，后端按表间父子关系回溯到 info 主表收集查重范围， 再逐层下钻到字段所在表完成查重。
 *
 * <p>人员字段（{@code member.name}/{@code member.mobile_phone_no}/{@code member.identity_no}）按三者组合判断，
 * 编辑已保存的人员行时需额外传入人员行ID以补齐其余字段并排除自身。
 */
public interface ExternalObjectDuplicateCheckApi {

    /**
     * 查重单个字段。
     *
     * <p>人员姓名/手机号/证件号为组合查重，需要按 {@code subId} 读取当前人员行补齐其余两个字段； 其他字段只需字段标识与字段值。
     *
     * @param req 查重请求；字段标识（table.field）与字段值必填
     * @return 查重结果
     */
    ExternalObjectDuplicateCheckResp check(ExternalObjectDuplicateCheckReq req);
}
