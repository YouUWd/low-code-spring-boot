package com.jdec.platform.dataengine.biz.context;

public class ModuleDataSaveContextHolder {

    private static final ThreadLocal<Boolean> OPEN_DATA_CHANGE_FLAG = new ThreadLocal<>();

    public static void openDataChangeFlag() {
        if (!getDataChangeFlag()) {
            OPEN_DATA_CHANGE_FLAG.set(Boolean.TRUE);
        }
    }

    public static Boolean getDataChangeFlag() {
        return OPEN_DATA_CHANGE_FLAG.get() != null && OPEN_DATA_CHANGE_FLAG.get();
    }
}
