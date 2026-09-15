package com.jdec.platform.dataengine.biz.context;

import cn.hutool.core.util.IdUtil;
import com.jdec.platform.dataengine.biz.enums.SaveType;

public class ModuleDataSaveContextHolder {

    private static final ThreadLocal<ModuleDataSaveContext> OPEN_DATA_CHANGE_FLAG =
            new ThreadLocal<>();

    public static void openDataChangeFlag(SaveType type) {
        if (!isOpenDataChangeFlag()) {
            OPEN_DATA_CHANGE_FLAG.set(
                    ModuleDataSaveContext.builder()
                            .type(type)
                            .actionId(IdUtil.getSnowflakeNextId())
                            .build());
        }
    }

    public static Boolean isOpenDataChangeFlag() {
        return OPEN_DATA_CHANGE_FLAG.get() != null
                && OPEN_DATA_CHANGE_FLAG.get().getActionId() != null;
    }

    public static Long getContextActionId() {
        if (isOpenDataChangeFlag()) {
            return OPEN_DATA_CHANGE_FLAG.get().getActionId();
        }
        return null;
    }

    public static void clear() {
        OPEN_DATA_CHANGE_FLAG.remove();
    }
}
