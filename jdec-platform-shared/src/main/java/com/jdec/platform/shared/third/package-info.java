/**
 * 第三方服务集成统一管理包。
 *
 * <p>集中管理所有第三方 HTTP API 的调用，提供统一的基础设施：
 *
 * <ul>
 *   <li>{@link com.jdec.platform.shared.third.ExternalApiProperties} - 统一配置属性
 *   <li>{@link com.jdec.platform.shared.third.BaseApiClient} - HTTP 客户端基类
 *   <li>{@link com.jdec.platform.shared.third.SignatureProvider} - 签名接口
 *   <li>{@link com.jdec.platform.shared.third.WordApiClient} - Word API 客户端
 * </ul>
 *
 * <p>新增第三方服务时，继承 {@code BaseApiClient} 并在 {@code ExternalApiAutoConfiguration} 中注册 Bean。
 */
package com.jdec.platform.shared.third;
