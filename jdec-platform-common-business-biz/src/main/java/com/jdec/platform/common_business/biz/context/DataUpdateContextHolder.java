package com.jdec.platform.common_business.biz.context;

import com.jdec.platform.common_business.biz.change.ModuleDataChangeContext;
import com.jdec.platform.common_business.biz.enums.DataTrackTypeEnum;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DataUpdateContextHolder {

    private static final ThreadLocal<List<Object>> DATA_CHANGE_CONTEXT = new ThreadLocal<>();

    private static final ThreadLocal<ModuleDataChangeContext> MODULE_DATA_CHANGE_CONTEXT =
            new ThreadLocal<>();

    private static final ThreadLocal<LinkedList<DataTrackTypeEnum>> OPEN_DATA_CHANGE_FLAG_STACK =
            new ThreadLocal<>();

    private static final ThreadLocal<Boolean> OPEN_DATA_CHANGE_FLAG = new ThreadLocal<>();

    /** 设置当前用户 token */
    public static void addDataUpdateToContext(Object o) {
        if (!getDataChangeFlag()) {
            return;
        }
        if (getDataUpdateContext() != null) {
            getDataUpdateContext().add(o);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(o);
            DATA_CHANGE_CONTEXT.set(list);
        }
    }

    public static void openDataChangeFlag() {
        if (!getDataChangeFlag()) {
            OPEN_DATA_CHANGE_FLAG.set(Boolean.TRUE);
        }
    }

    public static Boolean getDataChangeFlag() {
        return OPEN_DATA_CHANGE_FLAG.get() != null && OPEN_DATA_CHANGE_FLAG.get();
    }

    //    public static DataTrackTypeEnum getDataChangeFlagType() {
    //        return OPEN_DATA_CHANGE_FLAG.get();
    //    }

    //    public static int getDataChangeFlagSize() {
    //        return OPEN_DATA_CHANGE_FLAG.get() != null? OPEN_DATA_CHANGE_FLAG.get().size():0;
    //    }
    //
    //    public static DataTrackTypeEnum getDataChangeFlagTop() {
    //        return OPEN_DATA_CHANGE_FLAG.get() != null?
    // OPEN_DATA_CHANGE_FLAG.get().peekLast():null;
    //    }
    //
    //
    //
    //    public static void  removeDataChangeFlag(){
    //        OPEN_DATA_CHANGE_FLAG.get().pollLast();
    //    }

    public static List<Object> getDataUpdateContext() {
        return DATA_CHANGE_CONTEXT.get();
    }

    public static ModuleDataChangeContext getModuleDataChangeContext() {
        return MODULE_DATA_CHANGE_CONTEXT.get();
    }

    public static void setModuleDataChangeContext(ModuleDataChangeContext context) {
        MODULE_DATA_CHANGE_CONTEXT.set(context);
    }

    /** 清理所有上下文 */
    public static void clear() {
        DATA_CHANGE_CONTEXT.remove();
        OPEN_DATA_CHANGE_FLAG.remove();
        MODULE_DATA_CHANGE_CONTEXT.remove();
    }
}
