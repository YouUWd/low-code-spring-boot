package com.jdec.platform.shared.validation.dynamic;

/**
 * 字段适用性结果。
 *
 * @param status 适用性状态
 * @param reason {@link ApplicabilityStatus#UNKNOWN} 时的原因
 */
public record ApplicabilityResult(ApplicabilityStatus status, String reason) {

    public static ApplicabilityResult applicable() {
        return new ApplicabilityResult(ApplicabilityStatus.APPLICABLE, null);
    }

    public static ApplicabilityResult notApplicable(String reason) {
        return new ApplicabilityResult(ApplicabilityStatus.NOT_APPLICABLE, reason);
    }

    public static ApplicabilityResult unknown(String reason) {
        return new ApplicabilityResult(ApplicabilityStatus.UNKNOWN, reason);
    }
}
