package com.jdec.platform.validation.externalobject.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.shared.validation.dynamic.DynamicRequestValidator;
import com.jdec.platform.shared.validation.dynamic.ModuleDefinitionRegistry;
import com.jdec.platform.shared.validation.dynamic.ValidationMode;
import com.jdec.platform.shared.validation.dynamic.ValidationOptions;
import com.jdec.platform.shared.validation.dynamic.ValidationResult;
import com.jdec.platform.validation.SystemValidator;
import com.jdec.platform.validation.SystemValidatorKey;
import com.jdec.platform.validation.ValidationRequest;
import com.jdec.platform.validation.externalobject.ExternalObjectFieldLabels;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 外部对象业务校验器。
 *
 * <p>注册 key：项目 {@code company} + 外部对象结构根模块 {@code 1001}。
 */
@Component
public class ExternalObjectValidator implements SystemValidator {

    private static final String PROJECT_NO = "company";

    /** 外部对象主档模块（结构根模块） */
    private static final Long MODULE_ID = Long.valueOf(ExternalObjectModules.INFO);

    /** 不拒绝“不适用但提交了值”的字段：政府/事业单位的统一社会信用代码非必填。 */
    private static final ValidationOptions OPTIONS =
            new ValidationOptions(ValidationMode.SUBMIT, false, true, false, 50, 20000);

    private final SystemValidatorKey key = SystemValidatorKey.of(PROJECT_NO, MODULE_ID);
    private final DynamicRequestValidator validator;

    public ExternalObjectValidator(ObjectMapper objectMapper) {
        ModuleDefinitionRegistry registry =
                ModuleDefinitionRegistry.of(
                        new ExternalObjectValidationDefinition().moduleDefinitions());
        this.validator = new DynamicRequestValidator(registry, objectMapper);
    }

    @Override
    public SystemValidatorKey key() {
        return key;
    }

    @Override
    public ValidationResult validate(ValidationRequest request) {
        return validator.validate(request.payload(), OPTIONS);
    }

    @Override
    public List<String> missingRequiredFields(ValidationResult result) {
        return ExternalObjectFieldLabels.missingRequiredFields(result);
    }
}
