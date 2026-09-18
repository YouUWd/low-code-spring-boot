package com.jdec.platform.shared.context;

/**
 * 请求快照的 ThreadLocal 持有器。
 *
 * <p>请求线程内由拦截器写入；异步线程内由 TaskDecorator 复制写入。
 */
public final class RequestContextSnapshotHolder {

    private static final ThreadLocal<RequestContextSnapshot> HOLDER = new ThreadLocal<>();

    private RequestContextSnapshotHolder() {}

    public static void set(RequestContextSnapshot snapshot) {
        HOLDER.set(snapshot);
    }

    public static RequestContextSnapshot get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
