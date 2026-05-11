package com.jdec.platform.shared.context;

import com.jdec.platform.shared.constants.SystemConstant;

/** 应用请求上下文，统一管理 projectNo 和 subjectId */
public class AppContext {

    private static final ThreadLocal<String> PROJECT_NO_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> SUBJECT_HOLDER = new ThreadLocal<>();

    /** 设置当前请求的 projectNo */
    public static void setProjectNo(String projectNo) {
        PROJECT_NO_HOLDER.set(projectNo);
    }

    /** 获取当前请求的 projectNo */
    public static String getProjectNo() {
        String projectNo = PROJECT_NO_HOLDER.get();
        return projectNo != null ? projectNo : SystemConstant.DEFAULT_PROJECT_NO;
    }

    /** 移除当前线程的 projectNo */
    public static void removeProjectNo() {
        PROJECT_NO_HOLDER.remove();
    }

    /** 设置当前请求的 subjectId */
    public static void setSubjectId(Long subjectId) {
        SUBJECT_HOLDER.set(subjectId);
    }

    /** 获取当前请求的 subjectId */
    public static Long getSubjectId() {
        Long subjectId = SUBJECT_HOLDER.get();
        return subjectId != null ? subjectId : SystemConstant.DEFAULT_SUBJECT;
    }

    /** 移除当前线程的 subjectId */
    public static void removeSubjectId() {
        SUBJECT_HOLDER.remove();
    }

    /** 移除当前线程所有上下文 */
    public static void remove() {
        PROJECT_NO_HOLDER.remove();
        SUBJECT_HOLDER.remove();
    }
}
