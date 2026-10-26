package com.jdec.platform.validation;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.validation.externalobject.ExternalObjectSaveData;
import com.jdec.platform.validation.externalobject.ExternalObjectSaveSnapshotConverter;
import com.jdec.platform.validation.externalobject.ExternalObjectSnapshot;
import com.jdec.platform.validation.externalobject.ExternalObjectSnapshotRepository;
import com.jdec.platform.validation.externalobject.ExternalObjectValidationPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 外部对象提交校验入口。
 *
 * <p>以主单ID（{@code info.id}，即 companyId）加载整单数据后交给 {@link ExternalObjectValidationPipeline} 分层校验：
 *
 * <ol>
 *   <li>必填校验：按模块树遍历并收集全部未填项；
 *   <li>业务规则校验：合作类型、登记状态、证件号格式、参保人数；
 *   <li>查重校验：外部对象全称、基本信息、人员姓名+手机号+证件号的组合重复。
 * </ol>
 *
 * <p>提交的主单ID没有值时无法按主单加载业务库，改为使用前端提交的嵌套模块数据构造整单快照（{@link
 * #checkOnSubmit}）：所有需要校验的模块都参与校验，前端未提交数据的模块按该模块校验全部不通过处理， 全部通过后才允许提交。
 *
 * <p>三层校验递进执行：前一层存在不通过提示时立即一次性返回该层全部提示，后续层级不再执行， 避免“字段没填却提示重复”这类相互矛盾的提示。
 */
@Service
@RequiredArgsConstructor
public class ExternalObjectSubmitCheckService {

    private final ExternalObjectSnapshotRepository snapshotRepository;

    private final ExternalObjectSaveSnapshotConverter snapshotConverter;

    private final ExternalObjectValidationPipeline validationPipeline;

    /**
     * 执行外部对象提交校验。
     *
     * @param companyId 外部对象主单ID（info.id）
     * @throws BusinessException 主单不存在或任一校验不通过
     */
    public void check(Long companyId) {
        check(companyId, null, null);
    }

    /**
     * 执行外部对象提交校验。
     *
     * <p>提交接口返回的业务编号与版本号可用时优先按二者定位本次保存后的主单，以覆盖变更申请生成新版本的情况；否则按主单ID加载。
     *
     * @param companyId 外部对象主单ID（info.id）
     * @param businessNo 业务编号
     * @param versionNo 版本号
     * @throws BusinessException 主单不存在或任一校验不通过
     */
    public void check(Long companyId, String businessNo, Integer versionNo) {
        ExternalObjectSnapshot snapshot;
        if (businessNo != null && !businessNo.isBlank() && versionNo != null) {
            snapshot = snapshotRepository.load(businessNo, versionNo);
        } else if (companyId != null && companyId > 0) {
            snapshot = snapshotRepository.load(companyId);
        } else {
            throw new BusinessException("外部对象ID不能为空");
        }
        validationPipeline.check(snapshot);
    }

    /**
     * 执行外部对象提交校验（提交的主单ID没有值时使用）。
     *
     * <p>主单ID没有值时无法按主单ID或业务编号加载业务库，改为使用前端提交的嵌套模块数据构造整单快照：
     *
     * <ol>
     *   <li>所有需要校验的模块都参与必填校验：模块没有提交任何数据时，该模块下的必填项全部不通过； 模块有记录时逐条记录校验每个需要校验的字段规则；
     *   <li>必填全部通过后，再依次执行业务规则校验与查重校验；
     *   <li>前一层存在不通过提示时立即一次性返回，全部通过后才允许提交。
     * </ol>
     *
     * @param nestedModuleData 提交请求中的模块嵌套数据
     * @throws BusinessException 前端数据为空或任一校验不通过
     */
    public void checkOnSubmit(NestedModuleDataDTO nestedModuleData) {
        if (nestedModuleData == null) {
            throw new BusinessException("外部对象数据不能为空");
        }
        ExternalObjectSaveData data = snapshotConverter.convert(nestedModuleData);
        validationPipeline.checkOnSubmit(data.snapshot(), data.basicInfoAbsentOnCreate());
    }
}
