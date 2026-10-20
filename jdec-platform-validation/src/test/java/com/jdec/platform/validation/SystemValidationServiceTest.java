package com.jdec.platform.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.validation.dynamic.ValidationError;
import com.jdec.platform.shared.validation.dynamic.ValidationErrorCodes;
import com.jdec.platform.shared.validation.dynamic.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemValidationServiceTest {

    private static final Long MODULE_ID = 1001L;
    private static final String PROJECT_NO = "company";
    private static final Long SUBJECT_ID = 0L;

    private StubValidator validator;
    private SystemValidationService service;

    @BeforeEach
    void setUp() {
        validator = new StubValidator();
        SystemValidatorRegistry registry = new SystemValidatorRegistry(List.of(validator));
        service = new SystemValidationService(registry);
    }

    @Test
    void matchedValidatorResultIsReturned() {
        ValidationResult failed =
                ValidationResult.of(
                        List.of(
                                new ValidationError(
                                        "root[0]",
                                        "1001",
                                        "info.company_name",
                                        ValidationErrorCodes.REQUIRED,
                                        "缺少必填字段")));
        validator.result = failed;

        ValidationResult result = service.validate(MODULE_ID, PROJECT_NO, SUBJECT_ID, new Object());

        assertSame(failed, result);
        assertEquals(1, validator.invocations);
    }

    @Test
    void unregisteredModuleIsSkipped() {
        ValidationResult result = service.validate(9999L, PROJECT_NO, SUBJECT_ID, new Object());

        assertTrue(result.valid());
        assertEquals(0, validator.invocations);
    }

    @Test
    void missingModuleIdIsSkipped() {
        ValidationResult result = service.validate(null, PROJECT_NO, SUBJECT_ID, new Object());

        assertTrue(result.valid());
        assertEquals(0, validator.invocations);
    }

    @Test
    void validateOrThrowThrowsValidationFailedException() {
        validator.result =
                ValidationResult.of(
                        List.of(
                                new ValidationError(
                                        "root[0]",
                                        "1001",
                                        "info.company_name",
                                        ValidationErrorCodes.REQUIRED,
                                        "缺少必填字段")));

        ValidationFailedException ex =
                assertThrows(
                        ValidationFailedException.class,
                        () ->
                                service.validateOrThrow(
                                        MODULE_ID, PROJECT_NO, SUBJECT_ID, new Object()));

        assertSame(validator.result, ex.getResult());
        assertTrue(ex.getMessage().contains("info.company_name"));
    }

    @Test
    void validateOrThrowCarriesMissingRequiredFields() {
        validator.result =
                ValidationResult.of(
                        List.of(
                                new ValidationError(
                                        "root[0]",
                                        "1001",
                                        "info.company_name",
                                        ValidationErrorCodes.REQUIRED,
                                        "缺少必填字段")));
        validator.missingFields = List.of("外部对象全称", "人员构成 - 手机号码");

        ValidationFailedException ex =
                assertThrows(
                        ValidationFailedException.class,
                        () ->
                                service.validateOrThrow(
                                        MODULE_ID, PROJECT_NO, SUBJECT_ID, new Object()));

        assertEquals(ValidationFailedException.MISSING_REQUIRED_MESSAGE, ex.getMessage());
        assertEquals(ApiCodeEnum.WARNING_MODAL.getCode(), ex.getCode());
        assertEquals(validator.missingFields, ex.getData());
        assertEquals(validator.missingFields, ex.getMissingRequiredFields());
    }

    private static final class StubValidator implements SystemValidator {

        private final SystemValidatorKey key = SystemValidatorKey.of(PROJECT_NO, MODULE_ID);
        private int invocations;
        private ValidationResult result = ValidationResult.passed();
        private List<String> missingFields = List.of();

        @Override
        public SystemValidatorKey key() {
            return key;
        }

        @Override
        public ValidationResult validate(ValidationRequest request) {
            invocations++;
            return result;
        }

        @Override
        public List<String> missingRequiredFields(ValidationResult result) {
            return missingFields;
        }
    }
}
