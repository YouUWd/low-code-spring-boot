package com.jdec.platform.config.biz.audit.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志 remark 结构工具。
 *
 * <p>系统设置类审计（定制化业务，未配置模块）沿用旧的处理方式：把「模块变更数组」整体放入 {@code change.sub}， {@code change}
 * 统一从「数组」改为「对象」。区别于数据引擎的 remark（主模块字段变更放 {@code change.main}，主模块整体放 {@code change.sub}）。
 */
public final class AuditRemarkUtil {

    private AuditRemarkUtil() {}

    /**
     * 将模块变更数组包装为 change 对象：{@code {"sub": moduleActions}}。
     *
     * @param moduleActions 模块变更数组（旧结构 change 直接承载的内容）
     * @return change 对象，仅含 sub 键
     */
    public static Map<String, Object> wrapChange(List<?> moduleActions) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("sub", moduleActions != null ? moduleActions : Collections.emptyList());
        return change;
    }
}
