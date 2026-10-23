package com.jdec.platform.validation;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审批链业务动作校验路由。
 *
 * <p>单据提交、审批通过、审批驳回共用同一套整单校验，按根模块ID分发到对应业务模块； 模块ID与 {@code config_center.sys_module.id} 对应，在代码中固定。
 * 当前接入外部对象管理，后续场地产线按各自模块ID在此追加。
 *
 * <p>保存校验（{@link #checkOnSave}）同样按根模块ID分发：无审批链保存直接生效，有审批链的新增保存（主单ID没有值）落库前也执行该校验，
 * 当前单据的数据完全取自前端提交的参数，仅查重会读取其他已保存对象作为比对基准。
 *
 * <p>提交校验时如果主单ID没有值，无法按主单加载业务库，改为由 {@link #checkOnSubmit} 使用前端提交的嵌套模块数据校验：
 * 所有需要校验的模块都参与，未提交数据的模块按该模块校验全部不通过处理，必须全部通过后才能提交。
 */
@Component
@RequiredArgsConstructor
public class SubmitCheckRouter {

    /** 外部对象管理根模块ID。 */
    private static final Long EXTERNAL_OBJECT_MODULE_ID = 1001L;

    private final ExternalObjectSubmitCheckService externalObjectSubmitCheckService;

    private final ExternalObjectSaveCheckService externalObjectSaveCheckService;

    /**
     * 执行提交校验。
     *
     * @param moduleId 根模块ID
     * @param recordId 根模块主表记录ID
     * @param businessNo 业务编号
     * @param versionNo 版本号
     */
    public void check(Long moduleId, Long recordId, String businessNo, Integer versionNo) {
        if (moduleId == null) {
            return;
        }
        if (EXTERNAL_OBJECT_MODULE_ID.equals(moduleId)) {
            externalObjectSubmitCheckService.check(recordId, businessNo, versionNo);
            return;
        }
        // 后续场地产线：按各自根模块ID在此追加对应提交校验
    }

    /**
     * 执行提交校验（提交的主单ID没有值时使用）。
     *
     * <p>主单ID没有值时无法按主单加载业务库，改为使用前端提交的模块嵌套数据执行整单校验； 前端未提交数据的模块按该模块校验全部不通过处理，所有需要校验的模块都通过后才允许提交。
     *
     * @param moduleId 根模块ID
     * @param nestedModuleData 提交请求中的模块嵌套数据
     */
    public void checkOnSubmit(Long moduleId, NestedModuleDataDTO nestedModuleData) {
        if (moduleId == null) {
            return;
        }
        if (EXTERNAL_OBJECT_MODULE_ID.equals(moduleId)) {
            externalObjectSubmitCheckService.checkOnSubmit(nestedModuleData);
            return;
        }
        // 后续场地产线：按各自根模块ID在此追加对应提交校验
    }

    /**
     * 执行保存校验（无审批链保存、有审批链的新增保存）。
     *
     * <p>当前单据的校验数据全部取自前端提交的参数，不按主单ID加载业务库；未提交的模块不参与校验，查重仅读取其他已保存对象。
     *
     * @param nestedModuleData 保存请求中的模块嵌套数据
     */
    public void checkOnSave(NestedModuleDataDTO nestedModuleData) {
        if (nestedModuleData == null || nestedModuleData.getModuleId() == null) {
            return;
        }
        if (EXTERNAL_OBJECT_MODULE_ID.equals(nestedModuleData.getModuleId())) {
            externalObjectSaveCheckService.check(nestedModuleData);
            return;
        }
        // 后续场地产线：按各自根模块ID在此追加对应保存校验
    }
}
