package com.jdec.platform.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 校验器注册表。
 *
 * <p>应用启动时收集容器中全部 {@link SystemValidator}，按「项目编码 + 模块标识 + 主体ID」建立内存索引：
 *
 * <ul>
 *   <li>精确 key（subjectId 非空）优先匹配；
 *   <li>未命中时回退到 subjectId 为 null 的通配 key。
 * </ul>
 *
 * <p>同一 key 注册多个校验器时直接启动失败，避免运行期选择歧义。
 */
@Slf4j
@Component
public class SystemValidatorRegistry {

    private final Map<String, SystemValidator> exactValidators;
    private final Map<String, SystemValidator> wildcardValidators;

    public SystemValidatorRegistry(List<SystemValidator> validators) {
        Map<String, SystemValidator> exact = new LinkedHashMap<>();
        Map<String, SystemValidator> wildcard = new LinkedHashMap<>();

        if (validators != null) {
            for (SystemValidator validator : validators) {
                SystemValidatorKey key = validator.key();
                String index = index(key.projectNo(), key.moduleId(), key.subjectId());
                Map<String, SystemValidator> target = key.subjectId() == null ? wildcard : exact;
                if (target.putIfAbsent(index, validator) != null) {
                    throw new IllegalStateException("校验器注册重复: " + key);
                }
                log.info(
                        "注册业务校验器: projectNo={}, moduleId={}, subjectId={}, validator={}",
                        key.projectNo(),
                        key.moduleId(),
                        key.subjectId(),
                        validator.getClass().getSimpleName());
            }
        }

        this.exactValidators = Collections.unmodifiableMap(exact);
        this.wildcardValidators = Collections.unmodifiableMap(wildcard);
        log.info("业务校验器注册完成: 精确 {} 个, 通配 {} 个", exactValidators.size(), wildcardValidators.size());
    }

    /**
     * 查找校验器：先精确匹配主体，再回退到通配。
     *
     * @return 未注册时返回 {@code null}
     */
    public SystemValidator find(String projectNo, Long moduleId, Long subjectId) {
        if (projectNo == null || projectNo.isBlank() || moduleId == null) {
            return null;
        }
        if (subjectId != null) {
            SystemValidator exact = exactValidators.get(index(projectNo, moduleId, subjectId));
            if (exact != null) {
                return exact;
            }
        }
        return wildcardValidators.get(index(projectNo, moduleId, null));
    }

    /** 已注册的全部校验器。 */
    public Collection<SystemValidator> all() {
        List<SystemValidator> all = new ArrayList<>(wildcardValidators.values());
        all.addAll(exactValidators.values());
        return Collections.unmodifiableList(all);
    }

    private static String index(String projectNo, Long moduleId, Long subjectId) {
        return projectNo + '\0' + moduleId + '\0' + subjectId;
    }
}
