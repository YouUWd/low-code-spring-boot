package com.jdec.platform.validation.externalobject.venueline;

/**
 * 场地产线业务涉及的模块 ID。
 *
 * <p>与 {@code config_center.sys_module}、{@code sys_menu} 中的配置保持一致。
 */
public final class VenueLineModules {

    /** 场地产线管理（venue_line） */
    public static final String VENUE_LINE = "1021";

    /** 场地信息（venue） */
    public static final String VENUE = "1032";

    /** 产线信息（line） */
    public static final String LINE = "1033";

    public static final String TAB_VENUE = "场地";
    public static final String TAB_LINE = "产线";

    private VenueLineModules() {}
}
