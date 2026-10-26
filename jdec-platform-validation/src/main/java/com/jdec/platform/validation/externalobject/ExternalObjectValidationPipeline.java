package com.jdec.platform.validation.externalobject;

import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 外部对象分层校验执行器。
 *
 * <p>校验按 必填 → 业务规则 → 查重 三层递进执行，层内把全部不通过项收集完再返回，前一层不通过时后续层级不再执行：
 *
 * <ul>
 *   <li>必填未通过时字段本身没有值，格式校验与查重都无从谈起，避免“字段没填却提示重复”这类无意义或相互矛盾的提示；
 *   <li>业务规则先于查重执行：先纠正格式与取值，再判断是否与其他已保存数据重复；
 *   <li>只有前一层全部通过才进入下一层，三层全部通过才允许保存或提交。
 * </ul>
 *
 * <p>每层的不通过提示由 {@link ExternalObjectValidationMessages#throwIfNotEmpty()} 一次性抛出，前端本次只会收到 当前层级的全部提示。
 */
@Component
@RequiredArgsConstructor
public class ExternalObjectValidationPipeline {

    private final ExternalObjectRequiredValidator requiredValidator;

    private final ExternalObjectBusinessValidator businessValidator;

    private final ExternalObjectDuplicateValidator duplicateValidator;

    /**
     * 提交整单校验（按主单ID或业务编号加载的整单数据）。
     *
     * @param snapshot 外部对象整单数据
     */
    public void check(ExternalObjectSnapshot snapshot) {
        run(
                ExternalObjectCheckStage.REQUIRED,
                messages -> requiredValidator.validate(snapshot, messages));
        run(
                ExternalObjectCheckStage.BUSINESS,
                messages -> businessValidator.validate(snapshot, messages));
        run(
                ExternalObjectCheckStage.DUPLICATE,
                messages -> duplicateValidator.validate(snapshot, messages));
    }

    /**
     * 提交校验（提交的主单ID没有值时，基于前端提交的嵌套模块数据）。
     *
     * @param snapshot 由前端提交参数转换得到的整单数据
     * @param basicInfoAbsentOnCreate 无主单ID且未提交基本信息数据：基本信息的所有必填规则视为未满足
     */
    public void checkOnSubmit(ExternalObjectSnapshot snapshot, boolean basicInfoAbsentOnCreate) {
        run(
                ExternalObjectCheckStage.REQUIRED,
                messages ->
                        requiredValidator.validateOnSubmit(
                                snapshot, basicInfoAbsentOnCreate, messages));
        run(
                ExternalObjectCheckStage.BUSINESS,
                messages -> businessValidator.validate(snapshot, messages));
        run(
                ExternalObjectCheckStage.DUPLICATE,
                messages -> duplicateValidator.validate(snapshot, messages));
    }

    /**
     * 保存校验：只校验本次提交的模块。
     *
     * @param snapshot 由前端提交参数转换得到的整单数据
     * @param tables 本次提交且需要校验的模块表名
     * @param basicInfoAbsentOnCreate 无主单ID且未提交基本信息数据：基本信息的所有必填规则视为未满足
     */
    public void checkOnSave(
            ExternalObjectSnapshot snapshot, Set<String> tables, boolean basicInfoAbsentOnCreate) {
        run(
                ExternalObjectCheckStage.REQUIRED,
                messages ->
                        requiredValidator.validateOnSave(
                                snapshot, tables, basicInfoAbsentOnCreate, messages));
        run(
                ExternalObjectCheckStage.BUSINESS,
                messages -> businessValidator.validate(snapshot, messages));
        run(
                ExternalObjectCheckStage.DUPLICATE,
                messages -> duplicateValidator.validate(snapshot, messages));
    }

    /** 执行单层校验：收集该层全部提示，存在不通过项时立即抛出，后续层级不再执行。 */
    private static void run(
            ExternalObjectCheckStage stage, Consumer<ExternalObjectValidationMessages> validator) {
        ExternalObjectValidationMessages messages = new ExternalObjectValidationMessages(stage);
        validator.accept(messages);
        messages.throwIfNotEmpty();
    }
}
