package com.jdec.platform.validation;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jdec.platform.shared.validation.dynamic.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemValidatorRegistryTest {

    @Test
    void exactSubjectTakesPrecedenceOverWildcard() {
        SystemValidator wildcard = new StubValidator(SystemValidatorKey.of("company", 1001L));
        SystemValidator exact = new StubValidator(new SystemValidatorKey("company", 1001L, 0L));

        SystemValidatorRegistry registry = new SystemValidatorRegistry(List.of(wildcard, exact));

        assertSame(exact, registry.find("company", 1001L, 0L));
        assertSame(wildcard, registry.find("company", 1001L, 1L));
    }

    @Test
    void wildcardMatchesAnySubject() {
        SystemValidator wildcard = new StubValidator(SystemValidatorKey.of("company", 1021L));

        SystemValidatorRegistry registry = new SystemValidatorRegistry(List.of(wildcard));

        assertSame(wildcard, registry.find("company", 1021L, 0L));
        assertSame(wildcard, registry.find("company", 1021L, 123L));
    }

    @Test
    void unknownProjectOrModuleReturnsNull() {
        SystemValidatorRegistry registry =
                new SystemValidatorRegistry(
                        List.of(new StubValidator(SystemValidatorKey.of("company", 1001L))));

        assertNull(registry.find("other", 1001L, 0L));
        assertNull(registry.find("company", 2002L, 0L));
        assertNull(registry.find(null, 1001L, 0L));
        assertNull(registry.find("company", null, 0L));
    }

    @Test
    void duplicateKeyFailsFast() {
        SystemValidator first = new StubValidator(SystemValidatorKey.of("company", 1001L));
        SystemValidator second = new StubValidator(SystemValidatorKey.of("company", 1001L));

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> new SystemValidatorRegistry(List.of(first, second)));
        assertTrue(ex.getMessage().contains("moduleId=1001"));
    }

    private static final class StubValidator implements SystemValidator {

        private final SystemValidatorKey key;

        private StubValidator(SystemValidatorKey key) {
            this.key = key;
        }

        @Override
        public SystemValidatorKey key() {
            return key;
        }

        @Override
        public ValidationResult validate(ValidationRequest request) {
            return ValidationResult.passed();
        }
    }
}
