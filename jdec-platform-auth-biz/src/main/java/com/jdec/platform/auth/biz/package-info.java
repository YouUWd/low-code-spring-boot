/**
 * Auth 模块业务实现
 *
 * <p><b>访问控制：</b>
 *
 * <ul>
 *   <li>❌ 其他模块不能依赖此模块
 *   <li>✅ 仅供内部使用
 *   <li>✅ 其他模块应该通过 auth.api 模块来调用 Auth 功能
 * </ul>
 *
 * <p>该包包含 Auth 模块的业务逻辑实现，仅供内部使用。 其他模块应该通过 auth.api 模块来调用 Auth 功能。
 *
 * <p><b>依赖关系：</b>
 *
 * <ul>
 *   <li>依赖 auth.api - 自己的 API 接口
 *   <li>依赖 config.api - 配置中心接口（加载用户和角色信息）
 *   <li>依赖 shared - 共享工具和配置
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Auth BIZ",
        allowedDependencies = {"auth.api", "config.api", "shared"})
package com.jdec.platform.auth.biz;
