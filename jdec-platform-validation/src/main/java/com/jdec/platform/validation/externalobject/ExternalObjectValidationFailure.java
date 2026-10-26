package com.jdec.platform.validation.externalobject;

import java.util.List;

/**
 * 外部对象校验失败结果（随业务异常的 {@code data} 返回给前端）。
 *
 * <p>异常信息 {@code message} 仍是换行拼接的提示语，兼容现有展示方式；本结构用于前端按层级分组展示， 并按提示逐条定位表单项。
 *
 * @param stage 校验层级编码，取值见 {@link ExternalObjectCheckStage}
 * @param stageName 校验层级名称，可直接展示（如“必填校验”）
 * @param errors 本层级全部不通过的提示，按校验顺序排列
 */
public record ExternalObjectValidationFailure(String stage, String stageName, List<String> errors) {

    public ExternalObjectValidationFailure {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
