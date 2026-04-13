package com.jdec.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith 模块化架构验证测试。
 *
 * <p>验证以下内容：
 *
 * <ul>
 *   <li>所有模块可被正确识别
 *   <li>{@code internal} 包中的类不被外部模块直接引用
 *   <li>模块间无循环依赖
 * </ul>
 */
class ModularityTests {

    @Test
    void verifyModularStructure() {
        ApplicationModules modules = ApplicationModules.of(PlatformApplication.class);

        // 打印模块结构（便于开发调试）
        modules.forEach(System.out::println);

        // 验证模块边界 — 违规时抛出异常使测试失败
        try {
            modules.verify();
        } catch (Exception e) {
            System.out.println("=== 模块化验证异常 ===");
            System.out.println(e.getMessage());
            throw e;
        }
    }
}
