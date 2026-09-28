package com.jdec.platform.dataengine.biz.enums;

public enum ModuleType {
    LIST("LIST"),
    DETAIL("DETAIL");

    private String key;

    private ModuleType(String key) {
        this.key = key;
    }

    public static ModuleType getModuleType(String key) {
        for (ModuleType type : ModuleType.values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }

    public String getKey() {
        return key;
    }
}
