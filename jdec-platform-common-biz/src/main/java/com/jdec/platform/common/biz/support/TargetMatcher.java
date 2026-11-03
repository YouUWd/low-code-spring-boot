package com.jdec.platform.common.biz.support;

/**
 * Provider 生效目标匹配工具 (TargetMatcher)
 *
 * <p>统一的 target 规则：
 *
 * <ul>
 *   <li>{@code "*"} 或 {@code "*:*"}：全局匹配
 *   <li>{@code "company:*"}：匹配指定项目下的所有模块
 *   <li>{@code "company:COMPANY-ROOT"}：匹配指定项目下的指定模块编码
 *   <li>{@code "COMPANY-ROOT"} 或 {@code "*:COMPANY-ROOT"}：匹配任意项目下的指定模块编码
 *   <li>{@code "COMPANY-ROOT|WBDX"}：使用 {@code '|'} 分隔同时匹配多个规则
 * </ul>
 */
public final class TargetMatcher {

    private TargetMatcher() {}

    public static boolean matches(String target, String projectNo, String moduleCode) {
        if (isGlobal(target)) {
            return true;
        }
        if (target == null || target.isBlank()) {
            return false;
        }
        for (String singleTarget : target.split("\\|")) {
            String rule = singleTarget.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if (isGlobal(rule)) {
                return true;
            }
            if (rule.equalsIgnoreCase(projectNo + ":" + moduleCode)) {
                return true;
            }
            if (projectNo != null && rule.equalsIgnoreCase(projectNo + ":*")) {
                return true;
            }
            if (moduleCode != null
                    && (rule.equalsIgnoreCase(moduleCode)
                            || rule.equalsIgnoreCase("*:" + moduleCode))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGlobal(String rule) {
        return "*".equals(rule) || "*:*".equals(rule);
    }
}
