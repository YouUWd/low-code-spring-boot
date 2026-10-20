package com.jdec.platform.validation.externalobject.venueline;

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
 * 场地产线业务校验器。
 *
 * <p>注册 key：项目 {@code company} + 场地产线结构根模块 {@code 1021}。
 */
@Component
public class VenueLineValidator implements SystemValidator {

    private static final String PROJECT_NO = "company";

    /** 场地产线管理模块（结构根模块） */
    private static final Long MODULE_ID = Long.valueOf(VenueLineModules.VENUE_LINE);

    private static final ValidationOptions OPTIONS =
            new ValidationOptions(ValidationMode.SUBMIT, false, true, false, 50, 20000);

    private final SystemValidatorKey key = SystemValidatorKey.of(PROJECT_NO, MODULE_ID);
    private final DynamicRequestValidator validator;

    public VenueLineValidator(ObjectMapper objectMapper) {
        ModuleDefinitionRegistry registry =
                ModuleDefinitionRegistry.of(
                        new VenueLineValidationDefinition().moduleDefinitions());
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
