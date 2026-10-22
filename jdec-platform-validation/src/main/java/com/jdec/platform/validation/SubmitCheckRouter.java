package com.jdec.platform.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审批链业务动作校验路由。
 *
 * <p>单据提交、审批通过、审批驳回共用同一套整单校验，按根模块ID分发到对应业务模块； 模块ID与 {@code config_center.sys_module.id} 对应，在代码中固定。
 * 当前接入外部对象管理，后续场地产线按各自模块ID在此追加。
 */
@Component
@RequiredArgsConstructor
public class SubmitCheckRouter {

    /** 外部对象管理根模块ID。 */
    private static final Long EXTERNAL_OBJECT_MODULE_ID = 1001L;

    private final ExternalObjectSubmitCheckService externalObjectSubmitCheckService;

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
}
