package com.jdec.platform.dataengine.biz.enums;

import lombok.Getter;

@Getter
public enum SaveType {
    SAVE("save"),
    APPROVAL_CHANGE("change_save"),
    APPROVAL_REALTIME("realtime_save"),
    INSERT_AND_GET_ID("insert_and_get_id"),
    DELETE_BY_ID("delete_by_id"),
    ;

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
            case REAL_TIME_CHANGE -> {
                saveType = SaveType.APPROVAL_REALTIME;
            }
            case INSERT_AND_GET_ID -> {
                saveType = SaveType.INSERT_AND_GET_ID;
            }
            case DELETE_BY_ID -> {
                saveType = SaveType.DELETE_BY_ID;
            }
        }
        return saveType;
    }
}
