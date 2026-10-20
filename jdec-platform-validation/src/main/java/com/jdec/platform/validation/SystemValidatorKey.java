package com.jdec.platform.validation;

/**
 * 校验器注册 key。
 *
 * @param projectNo 项目编码（如 {@code company}）
 * @param moduleId 模块标识（{@code sys_module.id}，取请求中结构根模块的 moduleId）
 * @param subjectId 主体ID；为 {@code null} 时表示所有主体通配
 */
public record SystemValidatorKey(String projectNo, Long moduleId, Long subjectId) {

    public SystemValidatorKey {
        if (projectNo == null || projectNo.isBlank()) {
            throw new IllegalArgumentException("projectNo 不能为空");
        }
        if (moduleId == null) {
            throw new IllegalArgumentException("moduleId 不能为空");
        }
    }

    /** 通配所有主体的 key。 */
    public static SystemValidatorKey of(String projectNo, Long moduleId) {
        return new SystemValidatorKey(projectNo, moduleId, null);
    }
}
