package com.jdec.platform.externalobject.api;

import com.jdec.platform.externalobject.api.dto.request.ExternalObjectDuplicateCheckReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectDuplicateCheckResp;

/**
 * 外部对象字段查重接口。
 *
 * <p>前端只需传入 {@code table.field} 与字段值，后端按表间父子关系回溯到 info 主表收集查重范围， 再逐层下钻到字段所在表完成查重。
 */
public interface ExternalObjectDuplicateCheckApi {

    /**
     * 查重单个字段。
     *
     * @param req 查重请求；字段标识（table.field）与字段值必填
     * @return 查重结果
     */
    ExternalObjectDuplicateCheckResp check(ExternalObjectDuplicateCheckReq req);
}
