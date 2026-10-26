package com.jdec.platform.externalobject.api.dto.response;

/**
 * 外部对象字段实时校验结果。
 *
 * @param valid 是否校验通过
 * @param message 校验未通过时的错误提示；通过时为 {@code null}
 */
public record ExternalObjectRealtimeValidateResp(boolean valid, String message) {

    /** 校验通过。 */
    public static ExternalObjectRealtimeValidateResp ok() {
        return new ExternalObjectRealtimeValidateResp(true, null);
    }

    /** 校验未通过。 */
    public static ExternalObjectRealtimeValidateResp fail(String message) {
        return new ExternalObjectRealtimeValidateResp(false, message);
    }
}
