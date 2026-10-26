/**
 * 业务单据提交校验。
 *
 * <p>单据提交、审批通过、审批驳回统一调用 {@link com.jdec.platform.validation.SubmitCheckRouter}，
 * 由其按根模块ID分发到对应业务模块的整单校验； 当前接入外部对象管理。外部对象以主单ID（{@code info.id}）为入口，加载整单数据后由 {@link
 * com.jdec.platform.validation.externalobject.ExternalObjectValidationPipeline}
 * 分层执行必填校验、业务规则校验与查重校验： 每层内部收集全部不通过提示，前一层不通过时一次性返回该层提示，后续层级不再执行（字段未填时不做格式校验与查重）。
 *
 * <p>提交时主单ID没有值，改为使用前端提交的嵌套模块数据执行整单校验：所有需要校验的模块都参与， 前端未提交数据的模块按该模块校验全部不通过处理，全部通过后才允许提交。
 *
 * <p>校验逻辑由 {@link com.jdec.platform.validation.SubmitCheckTaskExecutor} 在独立线程执行后同步等待结果：
 * 调用方接口自带事务，事务内连接已锁定在开启事务时所用的库，校验切库切不走；独立线程重新按项目号路由到目标库， 而校验结果仍能带回调用线程，校验不通过照常中断业务。
 */
@ApplicationModule(displayName = "Validation", type = ApplicationModule.Type.OPEN)
package com.jdec.platform.validation;

import org.springframework.modulith.ApplicationModule;
