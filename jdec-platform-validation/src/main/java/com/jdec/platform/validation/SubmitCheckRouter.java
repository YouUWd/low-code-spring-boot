package com.jdec.platform.validation;

import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.shared.context.AppContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审批链业务动作校验路由。
 *
 * <p>单据提交、审批通过、审批驳回共用同一套整单校验，按根模块的唯一编码（{@code moduleCode}）分发到对应业务模块； 模块唯一编码与 {@code
 * config_center.sys_module.module_code} 对应，在代码中固定。当前接入外部对象管理，后续场地产线按各自模块唯一编码在此追加。
 *
 * <p>路由不仅匹配模块唯一编码，还需匹配项目编码（{@code AppContext.projectNo}）：不同项目的模块ID会重复，
 * 仅凭模块ID无法唯一确定外部对象，需同时按项目编码区分，避免其它项目的相同模块误命中外部对象校验。
 *
 * <p>保存校验（{@link #checkOnSave}）同样按根模块唯一编码分发：无审批链保存直接生效，有审批链的新增保存（主单ID没有值）落库前也执行该校验，
 * 当前单据的数据完全取自前端提交的参数，仅查重会读取其他已保存对象作为比对基准。变更申请单保存与变更提交（{@code btnType = change_save}）同样只校验前端提交的数据，
 * 但查重时按变更的原单据排除，避免与自身旧版本误报重复。
 *
 * <p>提交校验时如果主单ID没有值，无法按主单加载业务库，改为由 {@link #checkOnSubmit} 使用前端提交的嵌套模块数据校验：
 * 所有需要校验的模块都参与，未提交数据的模块按该模块校验全部不通过处理，必须全部通过后才能提交。
 *
 * <p>三个入口的校验逻辑都会交给 {@link SubmitCheckTaskExecutor} 在独立线程执行后同步等待结果：业务接口自带事务，
 * 事务内连接已锁定在开启事务时所用的库，校验再按业务库名取连接时切不过去；放到独立线程后可以重新路由到目标库， 而调用线程仍然拿得到校验结果，校验不通过照常中断业务。
 */
@Component
@RequiredArgsConstructor
public class SubmitCheckRouter {

    /**
     * 外部对象管理模块唯一编码集合。
     *
     * <p>测试环境为 {@code WBDX}；开发环境按子级从数据库读取获得，需将对应环境的唯一编码加入本集合。 路由时按「模块唯一编码 + 项目编码」双重匹配。
     */
    private static final Set<String> EXTERNAL_OBJECT_MODULE_CODES = Set.of("WBDX", "COMPANY-ROOT");

    /**
     * 外部对象所属项目编码集合。
     *
     * <p>不同项目的模块ID会重复，仅凭模块唯一编码仍可能跨项目误命中，需同时按项目编码区分。
     */
    private static final Set<String> EXTERNAL_OBJECT_PROJECT_CODES = Set.of("company");

    private final ExternalObjectSubmitCheckService externalObjectSubmitCheckService;

    private final ExternalObjectSaveCheckService externalObjectSaveCheckService;

    private final SubmitCheckTaskExecutor submitCheckTaskExecutor;

    private final SysModuleApi sysModuleApi;

    /**
     * 判断给定根模块ID是否属于外部对象管理。
     *
     * <p>需同时满足：该模块的唯一编码（{@code moduleCode}，按 moduleId 从配置库解析）在 {@link
     * #EXTERNAL_OBJECT_MODULE_CODES} 中，且当前请求的项目编码在 {@link #EXTERNAL_OBJECT_PROJECT_CODES} 中。 项目编码取自
     * {@link AppContext}（校验线程已复制调用线程上下文）。
     *
     * @param moduleId 根模块ID
     * @return 是否外部对象模块
     */
    private boolean isExternalObjectModule(Long moduleId) {
        if (moduleId == null) {
            return false;
        }
        String moduleCode = resolveModuleCode(moduleId);
        if (moduleCode == null) {
            return false;
        }
        String projectCode = AppContext.getProjectNo();
        return EXTERNAL_OBJECT_MODULE_CODES.contains(moduleCode)
                && EXTERNAL_OBJECT_PROJECT_CODES.contains(projectCode);
    }

    /** 按模块ID从配置库解析模块唯一编码；解析失败或不存在时返回 null。 */
    private String resolveModuleCode(Long moduleId) {
        try {
            SysModuleResp moduleResp =
                    sysModuleApi.getModuleCompleteById(
                            AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);
            return moduleResp != null && moduleResp.getModule() != null
                    ? moduleResp.getModule().getModuleCode()
                    : null;
        } catch (Exception e) {
            return null;
        }
    }

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
        if (isExternalObjectModule(moduleId)) {
            submitCheckTaskExecutor.runAndWait(
                    "外部对象提交校验",
                    () -> externalObjectSubmitCheckService.check(recordId, businessNo, versionNo));
            return;
        }
        // 后续场地产线：按各自模块唯一编码在此追加对应提交校验
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
        if (isExternalObjectModule(moduleId)) {
            submitCheckTaskExecutor.runAndWait(
                    "外部对象提交校验",
                    () -> externalObjectSubmitCheckService.checkOnSubmit(nestedModuleData));
            return;
        }
        // 后续场地产线：按各自模块唯一编码在此追加对应提交校验
    }

    /**
     * 执行保存校验（无审批链保存、有审批链的新增保存）。
     *
     * <p>当前单据的校验数据全部取自前端提交的参数，不按主单ID加载业务库；未提交的模块不参与校验，查重仅读取其他已保存对象。 变更申请单保存与变更提交（{@code btnType =
     * change_save}）都走本入口，查重按变更的原单据排除。
     *
     * @param nestedModuleData 保存请求中的模块嵌套数据
     */
    public void checkOnSave(NestedModuleDataDTO nestedModuleData) {
        if (nestedModuleData == null || nestedModuleData.getModuleId() == null) {
            return;
        }
        checkOnSave(nestedModuleData.getModuleId(), nestedModuleData, null);
    }

    /**
     * 执行保存校验（变更申请单保存 / 变更提交：按根模块路由，查重排除本次变更的原单据）。
     *
     * <p>校验数据全部取自前端提交的参数；查重时排除原单据（{@code excludeCompanyId}）及其同业务编号的其它版本， 避免变更后的数据与自身的旧版本误报重复。
     *
     * @param moduleId 根模块ID
     * @param nestedModuleData 变更类单据（变更申请单保存、变更提交）请求中的模块嵌套数据
     * @param excludeCompanyId 查重需要排除的原单据主单ID
     */
    public void checkOnSave(
            Long moduleId, NestedModuleDataDTO nestedModuleData, Long excludeCompanyId) {
        if (moduleId == null || nestedModuleData == null) {
            return;
        }
        if (isExternalObjectModule(moduleId)) {
            submitCheckTaskExecutor.runAndWait(
                    "外部对象保存校验",
                    () -> externalObjectSaveCheckService.check(nestedModuleData, excludeCompanyId));
            return;
        }
        // 后续场地产线：按各自模块唯一编码在此追加对应保存校验
    }
}
