package com.jdec.platform.externalobject.api;

import com.jdec.platform.externalobject.api.dto.request.ExternalObjectRealtimeValidateReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectRealtimeValidateResp;

/**
 * 外部对象字段实时校验接口（不含查重）。
 *
 * <p>供前端在表单字段编辑时实时调用，按 {@code table.field} 执行格式与关联校验；查重由 {@link ExternalObjectDuplicateCheckApi}
 * 独立提供。
 */
public interface ExternalObjectRealtimeValidateApi {

    /**
     * 实时校验单个字段。
     *
     * @param req 校验请求；字段标识（table.field）必填
     * @return 校验结果
     */
    ExternalObjectRealtimeValidateResp validate(ExternalObjectRealtimeValidateReq req);
}
