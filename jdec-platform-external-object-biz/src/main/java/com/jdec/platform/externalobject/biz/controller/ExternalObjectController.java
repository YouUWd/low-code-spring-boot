package com.jdec.platform.externalobject.biz.controller;

import com.jdec.platform.externalobject.api.ExternalObjectDuplicateCheckApi;
import com.jdec.platform.externalobject.api.ExternalObjectRealtimeValidateApi;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectDuplicateCheckReq;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectRealtimeValidateReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectDuplicateCheckResp;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectRealtimeValidateResp;
import com.jdec.platform.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 外部对象接口。 */
@RestController
@RequestMapping("/api/external-object/v1")
@RequiredArgsConstructor
@Tag(name = "外部对象", description = "外部对象实时校验与查重")
public class ExternalObjectController {

    private final ExternalObjectRealtimeValidateApi realtimeValidateApi;

    private final ExternalObjectDuplicateCheckApi duplicateCheckApi;

    /**
     * 字段实时校验（不含查重）。
     *
     * <p>前端编辑字段时实时触发；字段以 {@code table.field} 传参。
     */
    @PostMapping("/realtime-validate")
    @Operation(summary = "字段实时校验", description = "按 table.field 执行格式与关联校验，查重请调用 duplicate-check")
    public ApiResponse<ExternalObjectRealtimeValidateResp> realtimeValidate(
            @RequestBody ExternalObjectRealtimeValidateReq req) {
        return ApiResponse.success(realtimeValidateApi.validate(req));
    }

    /**
     * 字段查重。
     *
     * <p>前端只需传入 {@code table.field} 与字段值；查重范围由后端沿父子表关系解析。
     */
    @PostMapping("/duplicate-check")
    @Operation(summary = "字段查重", description = "按 table.field 与字段值查重，返回是否重复及命中的记录")
    public ApiResponse<ExternalObjectDuplicateCheckResp> duplicateCheck(
            @RequestBody ExternalObjectDuplicateCheckReq req) {
        return ApiResponse.success(duplicateCheckApi.check(req));
    }
}
