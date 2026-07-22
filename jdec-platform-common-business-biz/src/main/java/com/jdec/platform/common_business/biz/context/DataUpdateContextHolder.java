package com.jdec.platform.common_business.biz.context;

import java.util.ArrayList;
import java.util.List;

public class DataUpdateContextHolder {

    private static final ThreadLocal<List<Object>> DATA_CHANGE_CONTEXT = new ThreadLocal<>();

    private static final ThreadLocal<Boolean> OPEN_DATA_CHANGE_FLAG = new ThreadLocal<>();

    /** 设置当前用户 token */
    public static void addDataUpdateToContext(Object o) {
        if (getDataUpdateContext() != null) {
            getDataUpdateContext().add(o);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(o);
            DATA_CHANGE_CONTEXT.set(list);
        }
    }

    public static void openDataChangeFlag() {
        OPEN_DATA_CHANGE_FLAG.set(Boolean.TRUE);
    }

    public static Boolean getDataChangeFlag() {
        return OPEN_DATA_CHANGE_FLAG.get() != null && OPEN_DATA_CHANGE_FLAG.get();
    }

    /** 获取当前用户 token */
    public static List<Object> getDataUpdateContext() {
        return DATA_CHANGE_CONTEXT.get();
    }

    /** 清理所有上下文 */
    public static void clear() {
        DATA_CHANGE_CONTEXT.remove();
        OPEN_DATA_CHANGE_FLAG.remove();
    }
}
