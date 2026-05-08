package com.jdec.platform.shared.context;


import com.jdec.platform.shared.constants.SystemConstant;

/**
 * AppCode 请求上下文
 */
public class SubjectContext {

    private static final ThreadLocal<Integer> SUBJECT_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前请求的 subject
     */
    public static void set(Integer subject) {
        SUBJECT_HOLDER.set(subject);
    }

    /**
     * 获取当前请求的 subject
     */
    public static Integer get() {
        Integer subject = SUBJECT_HOLDER.get();
        return subject != null ? subject : SystemConstant.DEFAULT_SUBJECT;
    }

    /**
     * 移除当前线程的 subject
     */
    public static void remove() {
        SUBJECT_HOLDER.remove();
    }
}
