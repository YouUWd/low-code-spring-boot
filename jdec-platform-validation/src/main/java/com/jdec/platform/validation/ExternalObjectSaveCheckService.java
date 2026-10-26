package com.jdec.platform.validation;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.validation.externalobject.ExternalObjectBusinessValidator;
import com.jdec.platform.validation.externalobject.ExternalObjectDuplicateValidator;
import com.jdec.platform.validation.externalobject.ExternalObjectRequiredValidator;
import com.jdec.platform.validation.externalobject.ExternalObjectSaveData;
import com.jdec.platform.validation.externalobject.ExternalObjectSaveSnapshotConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 外部对象保存校验入口（无审批链保存、有审批链的新增保存）。
 *
 * <p>保存接口只拿到前端提交的 {@code nestedModuleData}，因此当前单据的校验数据完全基于请求参数，不按主单ID加载业务库：
 *
 * <ol>
 *   <li>必填校验：只校验本次提交的模块，未提交的模块不产生提示；
 *   <li>新增（无主单ID）时必须校验基本信息模块：未提交基本信息数据时，基本信息的所有必填规则都视为未满足；
 *   <li>业务规则校验：统一社会信用代码与身份证号格式、参保人数、合作类型、登记状态、人员信息一致性；
 *   <li>查重校验：全称、统一社会信用代码、简称、英文名称、人员手机号与证件号与其他已保存对象比对，当前对象按主单ID、来源主单ID、业务编号排除；
 *   <li>查重会读取其他已保存对象作为比对基准，失败时记录日志并跳过，不阻断保存。
 * </ol>
 *
 * <p>任意一步不通过即抛出 {@link com.jdec.platform.shared.exception.BusinessException} 返回前端。
 */
@Service
@RequiredArgsConstructor
public class ExternalObjectSaveCheckService {

    private final ExternalObjectSaveSnapshotConverter snapshotConverter;

    private final ExternalObjectRequiredValidator requiredValidator;

    private final ExternalObjectBusinessValidator businessValidator;

    private final ExternalObjectDuplicateValidator duplicateValidator;

    /**
     * 执行外部对象保存校验。
     *
     * @param nestedModuleData 前端提交的模块嵌套数据
     */
    public void check(NestedModuleDataDTO nestedModuleData) {
        if (nestedModuleData == null
                || nestedModuleData.getRecord() == null
                || nestedModuleData.getRecord().isEmpty()) {
            return;
        }
        ExternalObjectSaveData data = snapshotConverter.convert(nestedModuleData);
        requiredValidator.validateOnSave(
                data.snapshot(), data.tables(), data.basicInfoAbsentOnCreate());
        businessValidator.validate(data.snapshot());
        duplicateValidator.validate(data.snapshot());
    }
}
