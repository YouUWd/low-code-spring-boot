package com.jdec.platform.config.api.constant;

/** 配置中心常量类 */
public final class ConfigConstants {

    private ConfigConstants() {
        // utility class
    }

    /** 简单物理表类型 */
    public static final String TABLE_TYPE_SIMPLE = "SIMPLE";

    /** 复合/组合表类型 */
    public static final String TABLE_TYPE_COMBINE = "COMBINE";

    /** 简单物理字段类型 */
    public static final String FIELD_TYPE_SIMPLE = "SIMPLE";

    /** 复合/组合字段类型 */
    public static final String FIELD_TYPE_COMBINE = "COMBINE";

    /** 组合表虚拟表名 */
    public static final String TABLE_NAME_COMBINE = "*";

    /** 组合字段虚拟表描述 */
    public static final String TABLE_DESC_COMBINE = "组合字段";
}
