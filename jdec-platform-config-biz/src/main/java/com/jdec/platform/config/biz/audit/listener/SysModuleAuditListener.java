package com.jdec.platform.config.biz.audit.listener;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.biz.audit.event.SysModuleChangeEvent;
import com.jdec.platform.config.biz.audit.service.DataAuditService;
import com.jdec.platform.config.biz.audit.util.SysModuleAuditHelper;
import com.jdec.platform.config.biz.entity.SysDataSnapshot;
import com.jdec.platform.config.biz.service.SysDataSnapshotService;
import com.jdec.platform.config.biz.service.SysModuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 模块配置变更事件监听器
 *
 * <p>监听 SysModuleChangeEvent，专门负责模块变更审计日志的拼装与 HTTP 发送， 以及 sys_data_snapshot 数据快照表的同步写入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysModuleAuditListener {

    private final DataAuditService dataAuditService;
    private final SysModuleAuditHelper auditHelper;
    private final SysDataSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final SysModuleService sysModuleService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handleModuleChangeEvent(SysModuleChangeEvent event) {
        log.info(
                "收到模块配置变更事件: eventType={}, moduleId={}", event.getEventType(), event.getModuleId());

        switch (event.getEventType()) {
            case SAVE:
                handleSave(event);
                break;
            case DELETE:
                handleDelete(event);
                break;
            case MOVE:
                handleMove(event);
                break;
            default:
                log.warn("未知的模块事件类型: {}", event.getEventType());
        }
    }

    private void handleSave(SysModuleChangeEvent event) {
        Long targetId = event.getModuleId();
        if (targetId == null
                && event.getNewSaveRequest() != null
                && event.getNewSaveRequest().getModule() != null) {
            targetId = event.getNewSaveRequest().getModule().getId();
        }

        // 1. 如果是编辑场景，在写入新快照之前，先获取历史数据快照
        SaveModuleReq oldSaveRequest = event.getOldSaveRequest();
        if (oldSaveRequest == null && targetId != null) {
            SysDataSnapshot snapshot = snapshotService.getLatestSnapshot("sys_module", targetId);
            if (snapshot != null && StringUtils.hasText(snapshot.getJsonData())) {
                try {
                    oldSaveRequest =
                            objectMapper.readValue(snapshot.getJsonData(), SaveModuleReq.class);
                } catch (Exception e) {
                    log.warn("解析模块 {} 历史快照失败，将回退从数据库实时数据构建旧快照", targetId, e);
                }
            }
            if (oldSaveRequest == null) {
                SysModuleCompleteResp oldCompleteData =
                        sysModuleService.getModuleCompleteById(
                                event.getProjectNo(), event.getSubjectId(), targetId);
                if (oldCompleteData != null) {
                    oldSaveRequest = auditHelper.convertRespToReq(oldCompleteData);
                }
            }
        }

        // 2. 将最新的请求内容自动写入 / 更新到 sys_data_snapshot 快照表
        if (targetId != null && event.getNewSaveRequest() != null) {
            snapshotService.saveOrUpdateSnapshot(
                    "sys_module", targetId, JSONUtil.toJsonStr(event.getNewSaveRequest()));
            log.info("成功同步更新模块数据快照 sys_data_snapshot: tableName=sys_module, dataId={}", targetId);
        }

        // 3. 构建并发送审计日志
        if (oldSaveRequest == null) {
            // 真实全新新增场景
            String remarkJson =
                    auditHelper.buildSaveModuleRemark(
                            event.getProjectNo(),
                            event.getSubjectId(),
                            null,
                            event.getNewSaveRequest());
            dataAuditService.auditWithCustomRemark(
                    "系统配置", "模块配置", "新增模块", "sys_module", targetId, remarkJson);
        } else {
            // 编辑修改场景
            String remarkJson =
                    auditHelper.buildSaveModuleRemark(
                            event.getProjectNo(),
                            event.getSubjectId(),
                            oldSaveRequest,
                            event.getNewSaveRequest());
            dataAuditService.auditWithCustomRemark(
                    "系统配置", "模块配置", "修改模块", "sys_module", targetId, remarkJson);
        }
    }

    private void handleDelete(SysModuleChangeEvent event) {
        String remarkJson = auditHelper.buildDeleteModuleRemark(event.getDeletedCompleteData());

        // 发送删除审计日志
        dataAuditService.auditWithCustomRemark(
                "系统配置", "模块配置", "删除模块", "sys_module", event.getModuleId(), remarkJson);

        // 删除 sys_data_snapshot 快照表中的记录
        if (event.getModuleId() != null) {
            snapshotService.deleteSnapshot("sys_module", event.getModuleId());
            log.info(
                    "成功删除模块数据快照 sys_data_snapshot: tableName=sys_module, dataId={}",
                    event.getModuleId());
        }
    }

    private void handleMove(SysModuleChangeEvent event) {
        String remarkJson =
                auditHelper.buildMoveModuleRemark(
                        event.getModuleName(),
                        event.getOldParentName(),
                        event.getNewParentName(),
                        event.getMoveRequest());

        // 发送移动审计日志
        dataAuditService.auditWithCustomRemark(
                "系统配置", "模块配置", "移动模块", "sys_module", event.getModuleId(), remarkJson);
    }
}
