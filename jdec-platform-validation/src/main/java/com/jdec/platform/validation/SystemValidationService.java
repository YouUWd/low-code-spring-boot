package com.jdec.platform.validation;

import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.validation.dynamic.ValidationResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 业务校验统一入口。
 *
 * <p>调用方只需传入模块标识与前端请求体：
 *
 * <pre>{@code
 * ValidationResult result = systemValidationService.validate(moduleId, req);
 * // 或校验失败直接抛异常
 * systemValidationService.validateOrThrow(moduleId, req);
 * }</pre>
 *
 * <p>内部按「项目编码 + 模块标识 + 主体ID」从 {@link SystemValidatorRegistry} 中选择校验器。模块未注册校验器时直接放行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemValidationService {

    private final SystemValidatorRegistry registry;

    /** 使用当前请求上下文中的项目编码与主体ID。 */
    public ValidationResult validate(Long moduleId, Object payload) {
        return validate(moduleId, AppContext.getProjectNo(), AppContext.getSubjectId(), payload);
    }

    /** 显式指定项目编码与主体ID。 */
    public ValidationResult validate(
            Long moduleId, String projectNo, Long subjectId, Object payload) {
        return resolve(moduleId, projectNo, subjectId, payload).result();
    }

    /** 校验并在失败时抛出 {@link ValidationFailedException}。 */
    public void validateOrThrow(Long moduleId, Object payload) {
        validateOrThrow(moduleId, null, null, payload);
    }

    /** 显式指定项目编码与主体ID，失败时抛出 {@link ValidationFailedException}。 */
    public void validateOrThrow(Long moduleId, String projectNo, Long subjectId, Object payload) {
        Resolution resolution = resolve(moduleId, projectNo, subjectId, payload);
        if (!resolution.result().valid()) {
            List<String> missingRequiredFields =
                    resolution.validator() == null
                            ? List.of()
                            : resolution.validator().missingRequiredFields(resolution.result());
            throw new ValidationFailedException(resolution.result(), missingRequiredFields);
        }
    }

    private Resolution resolve(Long moduleId, String projectNo, Long subjectId, Object payload) {
        if (moduleId == null) {
            log.warn("业务校验跳过：请求未携带模块标识");
            return new Resolution(null, ValidationResult.passed());
        }

        String effectiveProjectNo =
                StringUtils.hasText(projectNo) ? projectNo : AppContext.getProjectNo();
        Long effectiveSubjectId = subjectId != null ? subjectId : AppContext.getSubjectId();

        SystemValidator validator = registry.find(effectiveProjectNo, moduleId, effectiveSubjectId);
        if (validator == null) {
            log.warn(
                    "业务校验跳过：未注册对应校验器, moduleId={}, projectNo={}, subjectId={}",
                    moduleId,
                    effectiveProjectNo,
                    effectiveSubjectId);
            return new Resolution(null, ValidationResult.passed());
        }

        ValidationResult result =
                validator.validate(
                        new ValidationRequest(
                                moduleId, effectiveProjectNo, effectiveSubjectId, payload));
        return new Resolution(validator, result == null ? ValidationResult.passed() : result);
    }

    /** 校验过程产物：命中的校验器（可能为空）与校验结果。 */
    private record Resolution(SystemValidator validator, ValidationResult result) {}
}
