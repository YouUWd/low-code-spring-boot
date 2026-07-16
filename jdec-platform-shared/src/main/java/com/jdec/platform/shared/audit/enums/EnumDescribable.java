package com.jdec.platform.shared.audit.enums;

/**
 * 可描述的枚举接口
 *
 * <p>用于审计日志枚举值转中文描述
 *
 * <p>枚举类实现此接口后，审计日志会自动将枚举值转换为中文描述
 */
public interface EnumDescribable {

    /**
     * 获取枚举的中文描述
     *
     * @return 中文描述
     */
    String getDescription();

    /**
     * 获取枚举的值（用于比较）
     *
     * @return 枚举值
     */
    default Object getValue() {
        if (this instanceof Enum<?>) {
            return ((Enum<?>) this).name();
        }
        return this.toString();
    }
}
