package com.jdec.platform.shared.validation.dynamic;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefinitionConfigValidatorTest {

    @Test
    void duplicateModuleDefinitionFailsFast() {
        ModuleDefinition first = moduleWithAlwaysApplicableField("m");
        ModuleDefinition second = moduleWithAlwaysApplicableField("m");

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> ModuleDefinitionRegistry.of(List.of(first, second)));
        assertTrue(error.getMessage().contains("模块定义重复"));
    }

    @Test
    void duplicateFieldFailsFast() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ModuleDefinition(
                                        "m",
                                        List.of(
                                                new FieldDefinition(
                                                        "m",
                                                        "m.a",
                                                        ValueType.STRING,
                                                        true,
                                                        AlwaysApplicable.INSTANCE,
                                                        List.of()),
                                                new FieldDefinition(
                                                        "m",
                                                        "m.a",
                                                        ValueType.STRING,
                                                        true,
                                                        AlwaysApplicable.INSTANCE,
                                                        List.of()))));
        assertTrue(error.getMessage().contains("重复字段"));
    }

    @Test
    void unknownFieldReferenceFailsFast() {
        ModuleDefinition module =
                new ModuleDefinition(
                        "m",
                        List.of(
                                new FieldDefinition(
                                        "m",
                                        "m.a",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.current("m.missing"), "x")),
                                        List.of())));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> ModuleDefinitionRegistry.of(List.of(module)));
        assertTrue(error.getMessage().contains("不存在的字段"));
    }

    @Test
    void unknownModuleReferenceFailsFast() {
        ModuleDefinition module =
                new ModuleDefinition(
                        "m",
                        List.of(
                                new FieldDefinition(
                                        "m",
                                        "m.a",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.parent("other", "other.type"),
                                                        "x")),
                                        List.of())));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> ModuleDefinitionRegistry.of(List.of(module)));
        assertTrue(error.getMessage().contains("不存在的模块"));
    }

    @Test
    void cyclicConditionDependencyFailsFast() {
        ModuleDefinition module =
                new ModuleDefinition(
                        "m",
                        List.of(
                                new FieldDefinition(
                                        "m",
                                        "m.a",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.current("m.b"), "x")),
                                        List.of()),
                                new FieldDefinition(
                                        "m",
                                        "m.b",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.current("m.a"), "x")),
                                        List.of())));

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> ModuleDefinitionRegistry.of(List.of(module)));
        assertTrue(error.getMessage().contains("循环依赖"));
    }

    @Test
    void moduleRecordsRequiresExplicitModuleId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FieldRef.module(CollectionQuantifier.ANY, null, "m.a", 0));
    }

    @Test
    void enumRuleIsValidControlFieldDefinition() {
        ModuleDefinition module =
                new ModuleDefinition(
                        "m",
                        List.of(
                                new FieldDefinition(
                                        "m",
                                        "m.type",
                                        ValueType.STRING,
                                        true,
                                        AlwaysApplicable.INSTANCE,
                                        List.of(new EnumRule(Set.of("a", "b")))),
                                new FieldDefinition(
                                        "m",
                                        "m.value",
                                        ValueType.STRING,
                                        true,
                                        new ConditionApplicability(
                                                FieldCompareCondition.eq(
                                                        FieldRef.current("m.type"), "a")),
                                        List.of(new RequiredRule()))));

        ModuleDefinitionRegistry registry = ModuleDefinitionRegistry.of(List.of(module));
        assertTrue(registry.get("m") != null);
    }

    private static ModuleDefinition moduleWithAlwaysApplicableField(String moduleId) {
        return new ModuleDefinition(
                moduleId,
                List.of(
                        new FieldDefinition(
                                moduleId,
                                moduleId + ".id",
                                ValueType.STRING,
                                true,
                                AlwaysApplicable.INSTANCE,
                                List.of())));
    }
}
