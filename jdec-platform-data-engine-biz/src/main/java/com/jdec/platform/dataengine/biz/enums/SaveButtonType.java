package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum SaveButtonType {
    SAVE("save", "保存"),
    CHANGE("change", "变更"),
    REAL_TIME_CHANGE("realTime", "实时保存"),
    ;

    private final String key;
    private final String title;

    SaveButtonType(String key, String title) {
        this.key = key;
        this.title = title;
    }

    public static SaveButtonType getSaveButtonTypeByKey(String keyStr) {
        for (SaveButtonType buttonType : SaveButtonType.values()) {
            if (buttonType.key.equals(keyStr)) {
                return buttonType;
            }
        }
        return null;
    }
}
