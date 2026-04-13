package com.jdec.platform.config.api.enums;

/**
 * 对齐方式枚举
 *
 * <p>定义字段在表格中的对齐方式。
 */
public enum AlignType {
    /** 左对齐 */
    LEFT("left", "左对齐"),

    /** 居中 */
    CENTER("center", "居中"),

    /** 右对齐 */
    RIGHT("right", "右对齐");

    private final String code;
    private final String description;

    AlignType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取对齐方式
     *
     * @param code 对齐编码
     * @return 对齐方式，如果不存在则返回 null
     */
    public static AlignType fromCode(String code) {
        for (AlignType type : AlignType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
