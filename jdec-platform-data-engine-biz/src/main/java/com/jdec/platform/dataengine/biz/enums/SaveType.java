package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum SaveType {
    SAVE("save"),
    APPROVAL_CHANGE("change_save"),
    APPROVAL_REALTIME_SAVE("realtime_save"),
    APPROVAL_SAVE("approval_save");

    private String btn;

    private SaveType(String btn) {
        this.btn = btn;
    }

    public static SaveType getSaveType(String btn) {

        SaveButtonType saveButtonType = SaveButtonType.getSaveButtonTypeByKey(btn);
        if (saveButtonType == null) {
            saveButtonType = SaveButtonType.SAVE;
        }
        SaveType saveType = null;
        switch (saveButtonType) {
            case SAVE -> {
                // 保存
                saveType = SaveType.SAVE;
            }
            case CHANGE -> {
                // 提交
                saveType = SaveType.APPROVAL_CHANGE;
            }
            case REAL_TIME_SAVE -> {
                saveType = SaveType.APPROVAL_REALTIME_SAVE;
            }
            case APPROVAL_SAVE -> {
                saveType = SaveType.APPROVAL_SAVE;
            }
        }
        return saveType;
    }
}
