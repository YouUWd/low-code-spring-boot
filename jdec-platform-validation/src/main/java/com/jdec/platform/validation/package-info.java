/**
 * 业务校验器注册中心。
 *
 * <p>各业务系统的校验规则按包隔离（如 {@code externalobject.external}、{@code externalobject.venueline}），启动时由 {@link
 * com.jdec.platform.validation.SystemValidatorRegistry} 收集全部 {@link
 * com.jdec.platform.validation.SystemValidator}，并按「项目编码 + 模块标识 + 主体ID」建立内存索引。
 *
 * <p>调用方只需通过 {@link com.jdec.platform.validation.SystemValidationService} 传入模块标识与请求体即可。
 */
@ApplicationModule(displayName = "Validation", type = ApplicationModule.Type.OPEN)
package com.jdec.platform.validation;

import org.springframework.modulith.ApplicationModule;
