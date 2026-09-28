/**
 * Common 模块业务实现。
 *
 * <p><b>访问控制：</b>
 *
 * <ul>
 *   <li>❌ 其他模块不能依赖此模块
 *   <li>✅ 仅供内部使用
 *   <li>✅ 其他模块应该通过 common.api 模块来调用 Common 功能
 * </ul>
 *
 * <p><b>依赖关系：</b>
 *
 * <ul>
 *   <li>依赖 common.api - 自己的 API 接口
 *   <li>依赖 config.api - 调用配置中心（用户树、模块、权限等）
 *   <li>依赖 shared - 共享工具和配置
 * </ul>
 *
 * <p><b>新增依赖时务必同步这里：</b>{@code allowedDependencies} 是白名单，代码引用了未列出的模块会让 {@code ModularityTests} 的
 * {@code ApplicationModules.verify()} 校验失败。例如要调用人力模块，需同时做两件事：
 *
 * <ol>
 *   <li>在本模块 {@code pom.xml} 加 {@code jdec-platform-hr-api} 依赖；
 *   <li>把 {@code "hr.api"} 加进下面的 {@code allowedDependencies}。
 * </ol>
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Common BIZ",
        allowedDependencies = {
            "common.api",
            "config.api",
            "config.open",
            "hr.api",
            "approval.api",
            "data.api",
            "dataengine.api",
            "shared"
        })
package com.jdec.platform.common.biz;
