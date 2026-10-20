package com.jdec.platform.validation;

import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.validation.dynamic.ValidationResult;
import java.util.List;

/** 业务校验未通过。异常携带完整校验结果，便于调用方回传结构化错误。 */
public class ValidationFailedException extends BusinessException {

    /** 存在必填项缺失时的提示文案。 */
    public static final String MISSING_REQUIRED_MESSAGE = "存在必填项未完成填写，请完善后再提交";

    private final transient ValidationResult result;
    private final List<String> missingRequiredFields;

    public ValidationFailedException(ValidationResult result) {
        this(result, List.of());
    }

    /**
     * @param result 原始校验结果
     * @param missingRequiredFields 「必填为空」字段的中文名称，随异常数据返回给前端；为空时沿用原始错误摘要
     */
    public ValidationFailedException(ValidationResult result, List<String> missingRequiredFields) {
        super(
                resolveCode(missingRequiredFields),
                resolveMessage(result, missingRequiredFields),
                resolveData(missingRequiredFields));
        this.result = result;
        this.missingRequiredFields =
                missingRequiredFields == null ? List.of() : List.copyOf(missingRequiredFields);
    }

    public ValidationResult getResult() {
        return result;
    }

    /** 「必填为空」字段的中文名称。 */
    public List<String> getMissingRequiredFields() {
        return missingRequiredFields;
    }

    private static int resolveCode(List<String> missingRequiredFields) {
        return isEmpty(missingRequiredFields)
                ? ApiCodeEnum.WARNING.getCode()
                : ApiCodeEnum.WARNING_MODAL.getCode();
    }

    private static String resolveMessage(
            ValidationResult result, List<String> missingRequiredFields) {
        if (!isEmpty(missingRequiredFields)) {
            return MISSING_REQUIRED_MESSAGE;
        }
        return result == null ? "校验未通过" : result.summary();
    }

    private static Object resolveData(List<String> missingRequiredFields) {
        return isEmpty(missingRequiredFields) ? null : List.copyOf(missingRequiredFields);
    }

    private static boolean isEmpty(List<String> missingRequiredFields) {
        return missingRequiredFields == null || missingRequiredFields.isEmpty();
    }
}
