package com.jdec.platform.validation;

import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.validation.externalobject.ExternalObjectBusinessValidator;
import com.jdec.platform.validation.externalobject.ExternalObjectDuplicateValidator;
import com.jdec.platform.validation.externalobject.ExternalObjectRequiredValidator;
import com.jdec.platform.validation.externalobject.ExternalObjectSnapshot;
import com.jdec.platform.validation.externalobject.ExternalObjectSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 外部对象提交校验入口。
 *
 * <p>以主单ID（{@code info.id}，即 companyId）加载整单数据后依次执行：
 *
 * <ol>
 *   <li>必填校验：命中第一个未填项即抛出异常；
 *   <li>业务规则校验：合作类型、登记状态、证件号格式、参保人数、人员信息一致性；
 *   <li>查重校验：外部对象全称、基本信息、人员手机号与证件号。
 * </ol>
 *
 * <p>三步均不做错误收集，任意一步不通过即抛出 {@link BusinessException} 返回前端。
 */
@Service
@RequiredArgsConstructor
public class ExternalObjectSubmitCheckService {

    private final ExternalObjectSnapshotRepository snapshotRepository;

    private final ExternalObjectRequiredValidator requiredValidator;

    private final ExternalObjectBusinessValidator businessValidator;

    private final ExternalObjectDuplicateValidator duplicateValidator;

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
        requiredValidator.validate(snapshot);
        businessValidator.validate(snapshot);
        duplicateValidator.validate(snapshot);
    }
}
