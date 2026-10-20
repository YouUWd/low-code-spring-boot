package com.jdec.platform.shared.validation.dynamic;

/**
 * 动态数据校验错误码。
 *
 * <p>错误码保持稳定，前端可按 {@code path + fieldKey + code} 定位并展示提示。
 */
public final class ValidationErrorCodes {

    /** 字段缺失或为空 */
    public static final String REQUIRED = "REQUIRED";

    /** 字段不在模块定义中 */
    public static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";

    /** 字段不允许客户端提交 */
    public static final String FIELD_NOT_WRITABLE = "FIELD_NOT_WRITABLE";

    /** 字段在当前条件下不适用，但请求提交了该字段 */
    public static final String FIELD_NOT_APPLICABLE = "FIELD_NOT_APPLICABLE";

    /** 无法确定字段是否适用（控制字段缺失、类型错误或引用目标不存在） */
    public static final String APPLICABILITY_UNKNOWN = "APPLICABILITY_UNKNOWN";

    /** 未知模块 */
    public static final String UNKNOWN_MODULE = "UNKNOWN_MODULE";

    /** 字段值类型不符合定义 */
    public static final String INVALID_TYPE = "INVALID_TYPE";

    /** 字段值不在枚举范围内 */
    public static final String ENUM_INVALID = "ENUM_INVALID";

    /** 字段长度不符合要求 */
    public static final String LENGTH_INVALID = "LENGTH_INVALID";

    /** 字段值不匹配正则 */
    public static final String PATTERN_INVALID = "PATTERN_INVALID";

    /** 字段值不在数值范围内 */
    public static final String RANGE_INVALID = "RANGE_INVALID";

    /** 记录的 row 必须是 JSON 对象 */
    public static final String ROW_REQUIRED = "ROW_REQUIRED";

    /** 请求整体结构非法 */
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    /** 模块 ID 缺失 */
    public static final String MODULE_ID_REQUIRED = "MODULE_ID_REQUIRED";

    /** record 必须是数组 */
    public static final String RECORD_REQUIRED = "RECORD_REQUIRED";

    /** 嵌套层级超过限制 */
    public static final String MAX_DEPTH_EXCEEDED = "MAX_DEPTH_EXCEEDED";

    /** 记录数量超过限制 */
    public static final String MAX_RECORDS_EXCEEDED = "MAX_RECORDS_EXCEEDED";

    private ValidationErrorCodes() {}
}
