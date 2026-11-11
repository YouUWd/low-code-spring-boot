package com.jdec.platform.dataengine.biz.decoupleprocess.listener;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.jdec.platform.common.api.MonitorLogApi;
import com.jdec.platform.common.api.dto.log.MonitorApproveOperation;
import com.jdec.platform.common.api.dto.log.MonitorContentSystem;
import com.jdec.platform.common.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.common.api.dto.log.MonitorMockInfo;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.config.api.SysConfigCategoryApi;
import com.jdec.platform.config.api.SysMenuApi;
import com.jdec.platform.config.api.dto.response.SysConfigItemResp;
import com.jdec.platform.config.api.dto.response.SysMenuResp;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.dataengine.api.dto.model.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.decoupleprocess.event.ModuleDataSaveChangeLogEvent;
import com.jdec.platform.dataengine.biz.operatelog.util.OperateLogRemarkUtil;
import com.jdec.platform.dataengine.biz.third.api.persistence.DataPersistenceUtil;
import com.jdec.platform.shared.constant.MonitorLogConstant;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.context.RequestContextSnapshot;
import com.jdec.platform.shared.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModuleDataChangeLogEventListener {

    private final OperateLogRemarkUtil operateLogRemarkUtil;

    private final OpenMenuApi openMenuApi;

    private final SysMenuApi sysMenuApi;

    private final MonitorLogApi monitorLogApi;

    private final SysConfigCategoryApi sysConfigCategoryApi;

    /** 操作动作：首次保存 */
    public static final String OPERATION_SAVE = "保存";

    /** 操作动作：修改 */
    public static final String OPERATION_UPDATE = "修改";

    /** 操作动作：删除 */
    public static final String OPERATION_DELETE = "删除";

    /** 通用配置分类：日志状态图标（图标地址存于配置项 extra） */
    private static final String LOG_STATE_ICON_CATEGORY = "logStateIcon";

    /** 日志状态：通过，首次保存动作取该图标 */
    private static final String LOG_STATE_PASS = "pass";

    /** 日志状态：提交，修改动作取该图标 */
    private static final String LOG_STATE_SUBMIT = "submit";

    /** 日志状态：驳回，删除动作取该图标 */
    private static final String LOG_STATE_REJECT = "reject";

    /** 无审批链时为手动操作 */
    private static final Integer APPROVAL_TYPE_MANUAL = 1;

    @Async
    @EventListener(ModuleDataSaveChangeLogEvent.class)
    public void handleEvent(ModuleDataSaveChangeLogEvent event) {
        log.info("Received change log event: {}", event);
        try {
            AppContext.setContext(
                    event.getSaveModel().getProjectNo(), event.getSaveModel().getSubjectId());
            UserContext.setLoginUser(event.getSaveModel().getLoginUser());

            // 首次保存不记录 remark；后续保存有旧数据时生成变更明细，无变更不记录日志
            Long menuId = resolveMenuId(event.getStructureRootModuleId());
            JsonNode remark = null;
            if (event.getOriginalData() != null) {
                remark =
                        operateLogRemarkUtil.buildRemark(
                                event.getOriginalData(),
                                event.getChangedData(),
                                event.getModuleCacheMap(),
                                menuId);
                if (remark == null) {
                    return;
                }
            }
            SysMenuResp menu = queryMenu(menuId);
            String menuName =
                    menu != null && StringUtils.isNotBlank(menu.getTitle())
                            ? menu.getTitle()
                            : moduleName(event);
            String operationResult = "已" + (event.getOriginalData() == null ? "保存" : "修改");
            MonitorLogSaveReq logReq = new MonitorLogSaveReq();
            logReq.setType(MonitorLogConstant.TYPE_OPERATION_LOG);
            logReq.setSubjectId(AppContext.getSubjectId());
            logReq.setApprove(
                    buildApprove(
                            menuName,
                            (event.getOriginalData() == null ? "保存" : "修改"),
                            operationResult));
            logReq.setContent(
                    buildContent(
                            menuName,
                            menu != null ? menu.getParam() : null,
                            operationResult,
                            resolveBusinessNo(event)));
            MonitorLoginInfo loginInfo = new MonitorLoginInfo();
            loginInfo.setUserId(UserContext.getRealUserId());
            logReq.setLoginInfo(loginInfo);
            MonitorMockInfo mockInfo = new MonitorMockInfo();
            mockInfo.setUserId(UserContext.getMockUserId());
            mockInfo.setFlag(UserContext.isMockLogin() ? 1 : 0);
            logReq.setMockInfo(mockInfo);
            RequestContextSnapshot snapshot = event.getRequestContextSnapshot();
            if (snapshot != null) {
                logReq.setIp(snapshot.getClientIp());
                logReq.setBrowser(snapshot.getBrowser());
                logReq.setPlatForm(snapshot.getPlatform());
            }
            // moduleId + dataId：monitor_operate_index 需要，用于按单据查询操作日志
            logReq.setModuleId(
                    event.getStructureRootModuleId() != null
                            ? event.getStructureRootModuleId()
                            : null);
            logReq.setDataId(event.getRootDataId() != null ? event.getRootDataId() : null);
            // remark 直接以 JSON 结构落库，避免监控日志侧二次序列化变成转义字符串
            if (remark != null) {
                logReq.setRemark(remark);
            }
            monitorLogApi.saveLog(logReq);
        } catch (Exception e) {
            log.error("记录操作日志失败 {},{}", event.getStructureRootModuleId(), event.getRootDataId(), e);
        } finally {
            AppContext.remove();
            UserContext.remove();
        }
    }

    /** 审批信息：无审批链为手动操作（approvalType=1） */
    private MonitorApproveOperation buildApprove(
            String menuName, String operation, String operationResult) {
        MonitorApproveOperation approve = new MonitorApproveOperation();
        approve.setLogSubType(MonitorLogConstant.OPERATION_LOG);
        approve.setName((menuName != null ? menuName : "") + operation);
        approve.setPath(resolveApprovePath(operation));
        approve.setResult(operationResult);
        approve.setApprovalType(APPROVAL_TYPE_MANUAL);
        return approve;
    }

    /** 按操作动作选择日志状态图标：首次保存取通过、修改取提交、删除取驳回 */
    private String resolveApprovePath(String operation) {
        if (OPERATION_SAVE.equals(operation)) {
            return queryLogStateIcon(LOG_STATE_PASS);
        }
        if (OPERATION_DELETE.equals(operation)) {
            return queryLogStateIcon(LOG_STATE_REJECT);
        }
        if (OPERATION_UPDATE.equals(operation)) {
            return queryLogStateIcon(LOG_STATE_SUBMIT);
        }
        return null;
    }

    /** 从通用配置「日志状态图标」读取图标地址（存于配置项 extra），配置缺失或查询失败返回 null 不影响日志落库 */
    private String queryLogStateIcon(String state) {
        try {
            SysConfigItemResp config =
                    sysConfigCategoryApi.getConfigItemsByValue(LOG_STATE_ICON_CATEGORY, state);
            if (config == null) {
                log.warn(
                        "通用配置缺少日志状态图标: categoryAlias={}, value={}", LOG_STATE_ICON_CATEGORY, state);
                return null;
            }
            return config.getExtra();
        } catch (Exception e) {
            log.warn("查询通用配置日志状态图标失败: value={}", state, e);
            return null;
        }
    }

    /** 日志内容：系统日志子类型，承载最外层菜单名称/参数与业务编码 */
    private MonitorContentSystem buildContent(
            String menuName, String menuParam, String operationResult, String businessNo) {
        MonitorContentSystem content = new MonitorContentSystem();
        content.setLogSubType(MonitorLogConstant.SYSTEM_LOG);
        content.setModuleName(menuName);
        content.setMenuParam(menuParam);
        content.setLinkName(businessNo);
        content.setOperation(operationResult);
        return content;
    }

    /** 业务编码：优先取保存响应，缺失时从请求行解析 */
    private String resolveBusinessNo(ModuleDataSaveChangeLogEvent event) {
        if (event.getOriginalData() != null) {
            return DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                    event.getOriginalData().getRecord().get(0).getRow(),
                    event.getModuleCacheMap()
                            .get(event.getStructureRootModuleId())
                            .getModuleInfo()
                            .getPrimaryTable());
        } else {
            return DataPersistenceUtil.getTableRowBusinessNoWithTabName(
                    event.getChangedData().getRecord().get(0).getRow(),
                    event.getModuleCacheMap()
                            .get(event.getStructureRootModuleId())
                            .getModuleInfo()
                            .getPrimaryTable());
        }
    }

    /** 菜单名称兜底：菜单查询不到时取当前结构根模块名称 */
    private String moduleName(ModuleDataSaveChangeLogEvent event) {
        SysModuleCombineInfo module =
                event.getModuleCacheMap().get(event.getStructureRootModuleId());
        return module != null && module.getModuleInfo() != null
                ? module.getModuleInfo().getModuleName()
                : null;
    }

    /** 按模块解析绑定菜单，失败时返回 null 不影响日志落库 */
    private Long resolveMenuId(Long moduleId) {
        if (moduleId == null) {
            return null;
        }
        try {
            return openMenuApi.getMenuIdByModuleId(moduleId);
        } catch (Exception e) {
            log.warn("查询模块绑定菜单失败，moduleId={}", moduleId, e);
            return null;
        }
    }

    /** 查询菜单（取名称与菜单参数），失败时返回 null 不影响日志落库 */
    private SysMenuResp queryMenu(Long menuId) {
        if (menuId == null) {
            return null;
        }
        try {
            return sysMenuApi.getMenuById(menuId);
        } catch (Exception e) {
            log.warn("查询菜单失败，menuId={}", menuId, e);
            return null;
        }
    }
}
