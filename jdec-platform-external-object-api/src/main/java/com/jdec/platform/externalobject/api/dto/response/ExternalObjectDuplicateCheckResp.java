package com.jdec.platform.externalobject.api.dto.response;

import java.util.List;

/**
 * 外部对象字段查重结果。
 *
 * @param duplicated 是否重复
 * @param message 重复时的提示信息；不重复时为 {@code null}
 * @param matches 命中的已保存记录；不重复时为空集合
 */
public record ExternalObjectDuplicateCheckResp(
        boolean duplicated, String message, List<ExternalObjectDuplicateMatch> matches) {

    public ExternalObjectDuplicateCheckResp {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    /** 未重复。 */
    public static ExternalObjectDuplicateCheckResp pass() {
        return new ExternalObjectDuplicateCheckResp(false, null, List.of());
    }

    /** 重复。 */
    public static ExternalObjectDuplicateCheckResp duplicated(
            String message, List<ExternalObjectDuplicateMatch> matches) {
        return new ExternalObjectDuplicateCheckResp(true, message, matches);
    }
}
