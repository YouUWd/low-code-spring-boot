package com.jdec.platform.externalobject.biz.controller;

import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectValidationResult;
import com.jdec.platform.externalobject.biz.validation.ExternalObjectFieldLabels;
import com.jdec.platform.shared.model.ApiResponse;
import com.jdec.platform.shared.validation.dynamic.DynamicRequestValidator;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationMode;
import com.jdec.platform.shared.validation.dynamic.ValidationOptions;
import com.jdec.platform.shared.validation.dynamic.ValidationResult;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部对象表单校验接口。
 *
 * <p>前端在调用 data-engine 保存接口之前先调用本接口；校验通过后再执行保存。
 */
@RestController
@RequestMapping("/api/external-object")
@RequiredArgsConstructor
public class ExternalObjectValidationController {

    /** 不拒绝“不适用但提交了值”的字段：政府/事业单位的统一社会信用代码非必填， 前端可能提交空字符串，此时该字段不适用但不应报错。 */
    private static final ValidationOptions VALIDATION_OPTIONS =
            new ValidationOptions(ValidationMode.SUBMIT, false, true, false, 50, 20000);

    private final DynamicRequestValidator dynamicRequestValidator;

    @PostMapping("/validate")
    public ApiResponse<ExternalObjectValidationResult> validate(@RequestBody DynamicSaveReq req) {
        ValidationResult result = dynamicRequestValidator.validate(req, VALIDATION_OPTIONS);

        Set<String> missingRequiredFields =
                result.errors().stream()
                        .filter(error -> ValidationErrorCodes.REQUIRED.equals(error.code()))
                        .map(ExternalObjectFieldLabels::displayName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        return ApiResponse.success(
                new ExternalObjectValidationResult(
                        result.valid(), missingRequiredFields, result.errors()));
    }
}
