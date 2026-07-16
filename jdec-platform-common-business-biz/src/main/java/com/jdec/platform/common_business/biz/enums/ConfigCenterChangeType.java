package com.jdec.platform.common_business.biz.enums;

public enum ConfigCenterChangeType {
    // MODULE_CHANGED,
    // ROLE_MENU_CHANGED,
    ROLE_MODULE_PERMISSION_CHANGED,
    // ROLE_CONFIG_CHANGED,
    ROLE_INTERACTION_PERMISSION_CHANGED,
    ROLE_DATA_PERMISSION_CHANGED,
    ROLE_SPECIAL_PERMISSION_CHANGED,
    ROLE_ADD,
    ROLE_DELETE,
    ;

    public static ConfigCenterChangeType getConfigCenterChangeType(String key) {
        if (key == null) {
            return null;
        }
        for (ConfigCenterChangeType type : ConfigCenterChangeType.values()) {
            if (type.name().equals(key)) {
                return type;
            }
        }
        return null;
    }
}
