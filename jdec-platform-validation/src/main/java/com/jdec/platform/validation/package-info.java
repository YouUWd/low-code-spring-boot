/**
 * 业务单据提交校验。
 *
 * <p>单据提交、审批通过、审批驳回统一调用 {@link com.jdec.platform.validation.SubmitCheckRouter}，
 * 由其按根模块ID分发到对应业务模块的整单校验； 当前接入外部对象管理。外部对象以主单ID（{@code info.id}）为入口，加载整单数据后依次执行必填校验、业务规则校验与查重校验，
 * 任一校验不通过即抛出 {@link com.jdec.platform.shared.exception.BusinessException} 返回前端，不做错误收集。
 */
@ApplicationModule(displayName = "Validation", type = ApplicationModule.Type.OPEN)
package com.jdec.platform.validation;

import org.springframework.modulith.ApplicationModule;
