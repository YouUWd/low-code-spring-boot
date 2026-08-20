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

    /** 设置上下文（保留现有的用户信息） */
    public static void setContext(String projectNo, Long subjectId) {
        AppRequestContext current = CONTEXT_HOLDER.get();
        if (current == null) {
            current = new AppRequestContext();
        }
        CONTEXT_HOLDER.set(
                new AppRequestContext(
                        projectNo, subjectId, current.getUserId(), current.getUserSubjectId()));
    }

    /** 一次性初始化上下文（包含用户信息，完全覆盖） */
    public static void setContext(
            String projectNo, Long subjectId, Long userId, Long userSubjectId) {
        CONTEXT_HOLDER.set(new AppRequestContext(projectNo, subjectId, userId, userSubjectId));
    }

    /** 设置用户信息（保留现有的 projectNo 和 subjectId） */
    public static void setUserInfo(Long userId, Long userSubjectId) {
        AppRequestContext current = CONTEXT_HOLDER.get();
        if (current == null) {
            current = new AppRequestContext();
        }
        CONTEXT_HOLDER.set(
                new AppRequestContext(
                        current.getProjectNo(), current.getSubjectId(), userId, userSubjectId));
    }

    /** 获取当前请求的 projectNo */
    public static String getProjectNo() {
        return getContext().getProjectNo();
    }

    /** 获取当前请求的 subjectId */
    public static Long getSubjectId() {
        return getContext().getSubjectId();
    }

    /** 获取当前登录用户的 userId */
    public static Long getUserId() {
        return getContext().getUserId();
    }

    /** 获取当前登录用户所属的主体ID */
    public static Long getUserSubjectId() {
        return getContext().getUserSubjectId();
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

        /** 主体ID（当前请求的主体） */
        private Long subjectId;

        /** 用户ID（当前登录用户） */
        private Long userId;

        /** 用户所属主体ID（当前登录用户自己的主体） */
        private Long userSubjectId;
    }
}
