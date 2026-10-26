package com.jdec.platform.shared.validation.dynamic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 模块定义注册表。
 *
 * <p>启动时收集全部 {@link ValidationDefinitionProvider}，并执行配置校验；配置非法会直接启动失败， 避免错误的条件引用或循环依赖进入运行期。
 */
@Component
public class ModuleDefinitionRegistry {

    private final Map<String, ModuleDefinition> modules;

    @Autowired
    public ModuleDefinitionRegistry(ObjectProvider<ValidationDefinitionProvider> providers) {
        this(
                providers == null
                        ? List.of()
                        : providers.stream().flatMap(p -> p.moduleDefinitions().stream()).toList());
    }

    private ModuleDefinitionRegistry(Collection<ModuleDefinition> definitions) {
        Map<String, ModuleDefinition> map = new LinkedHashMap<>();
        if (definitions != null) {
            for (ModuleDefinition definition : definitions) {
                if (map.putIfAbsent(definition.moduleId(), definition) != null) {
                    throw new IllegalStateException("模块定义重复: " + definition.moduleId());
                }
            }
        }
        DefinitionConfigValidator.validate(map);
        this.modules = Collections.unmodifiableMap(map);
    }

    /** 供非 Spring 环境或单元测试直接构建注册表。 */
    public static ModuleDefinitionRegistry of(Collection<ModuleDefinition> definitions) {
        return new ModuleDefinitionRegistry(definitions);
    }

    public static ModuleDefinitionRegistry empty() {
        return new ModuleDefinitionRegistry(List.<ModuleDefinition>of());
    }

    public ModuleDefinition get(String moduleId) {
        return modules.get(moduleId);
    }

    public Collection<ModuleDefinition> all() {
        return modules.values();
    }
}
