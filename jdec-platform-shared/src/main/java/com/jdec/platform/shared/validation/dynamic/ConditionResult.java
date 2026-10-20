package com.jdec.platform.shared.validation.dynamic;

/**
 * 条件求值结果。
 *
 * @param value 三态结果
 * @param reason {@link TruthValue#UNKNOWN} 时的原因
 */
public record ConditionResult(TruthValue value, String reason) {

    public static ConditionResult yes() {
        return new ConditionResult(TruthValue.TRUE, null);
    }

    public static ConditionResult no() {
        return new ConditionResult(TruthValue.FALSE, null);
    }

    public static ConditionResult unknown(String reason) {
        return new ConditionResult(TruthValue.UNKNOWN, reason);
    }
}
