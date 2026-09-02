package com.jdec.platform.config.biz.audit.event;

import com.jdec.platform.config.api.dto.request.MoveModuleReq;
import com.jdec.platform.config.api.dto.request.SaveModuleReq;
import com.jdec.platform.config.api.dto.response.SysModuleMetaResp;
import lombok.Getter;

/**
 * 模块配置变更事件
 *
 * <p>用于将 SysModuleService 业务逻辑与审计日志构建及发送解耦。
 */
@Getter
public class SysModuleChangeEvent {

    public enum EventType {
        SAVE,
        DELETE,
        MOVE
    }

    private final EventType eventType;
    private final Long moduleId;
    private final String projectNo;
    private final Long subjectId;

    // 保存场景属性：统一使用 SaveModuleReq 进行新旧比对
    private final SaveModuleReq oldSaveRequest;
    private final SaveModuleReq newSaveRequest;

    // 删除场景属性
    private final SysModuleMetaResp deletedCompleteData;

    // 移动场景属性
    private final MoveModuleReq moveRequest;
    private final String moduleName;
    private final String oldParentName;
    private final String newParentName;

    /** 保存/更新场景构造函数（前后数据统一为 SaveModuleReq） */
    public static SysModuleChangeEvent createSaveEvent(
            String projectNo,
            Long subjectId,
            Long moduleId,
            SaveModuleReq oldSaveRequest,
            SaveModuleReq newSaveRequest) {
        return new SysModuleChangeEvent(
                EventType.SAVE,
                moduleId,
                projectNo,
                subjectId,
                oldSaveRequest,
                newSaveRequest,
                null,
                null,
                null,
                null,
                null);
    }

    /** 删除场景构造函数 */
    public static SysModuleChangeEvent createDeleteEvent(
            String projectNo,
            Long subjectId,
            Long moduleId,
            SysModuleMetaResp deletedCompleteData) {
        return new SysModuleChangeEvent(
                EventType.DELETE,
                moduleId,
                projectNo,
                subjectId,
                null,
                null,
                deletedCompleteData,
                null,
                null,
                null,
                null);
    }

    /** 移动场景构造函数 */
    public static SysModuleChangeEvent createMoveEvent(
            String projectNo,
            Long subjectId,
            Long moduleId,
            MoveModuleReq moveRequest,
            String moduleName,
            String oldParentName,
            String newParentName) {
        return new SysModuleChangeEvent(
                EventType.MOVE,
                moduleId,
                projectNo,
                subjectId,
                null,
                null,
                null,
                moveRequest,
                moduleName,
                oldParentName,
                newParentName);
    }

    private SysModuleChangeEvent(
            EventType eventType,
            Long moduleId,
            String projectNo,
            Long subjectId,
            SaveModuleReq oldSaveRequest,
            SaveModuleReq newSaveRequest,
            SysModuleMetaResp deletedCompleteData,
            MoveModuleReq moveRequest,
            String moduleName,
            String oldParentName,
            String newParentName) {
        this.eventType = eventType;
        this.moduleId = moduleId;
        this.projectNo = projectNo;
        this.subjectId = subjectId;
        this.oldSaveRequest = oldSaveRequest;
        this.newSaveRequest = newSaveRequest;
        this.deletedCompleteData = deletedCompleteData;
        this.moveRequest = moveRequest;
        this.moduleName = moduleName;
        this.oldParentName = oldParentName;
        this.newParentName = newParentName;
    }
}
