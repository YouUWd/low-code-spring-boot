package com.jdec.platform.shared.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 应用请求上下文，统一管理 projectNo 和 subjectId */
public class AppContext {

    private static final ThreadLocal<AppRequestContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /** 获取当前线程的上下文对象，如果不存在则返回空对象（避免外部调用 NPE） */
    private static AppRequestContext getContext() {
        AppRequestContext context = CONTEXT_HOLDER.get();
        return context != null ? context : new AppRequestContext();
    }

    /** 一次性初始化上下文（真正的原子操作） 直接覆盖 ThreadLocal 中的引用，确保上下文快照的一致性 */
    public static void setContext(String projectNo, Long subjectId) {
        CONTEXT_HOLDER.set(new AppRequestContext(projectNo, subjectId));
    }

    /** 获取当前请求的 projectNo */
    public static String getProjectNo() {
        return getContext().getProjectNo();
    }

    /** 获取当前请求的 subjectId */
    public static Long getSubjectId() {
        return getContext().getSubjectId();
    }

    /** 移除当前线程的所有上下文信息 */
    public static void remove() {
        CONTEXT_HOLDER.remove();
    }

    /** 上下文实体类 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AppRequestContext {
        /** 项目编号 */
        private String projectNo;

        /** 主体ID */
        private Long subjectId;
    }
}
