package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.CreateScheduledTaskReq;
import com.jdec.platform.config.api.dto.response.ScheduledTaskResp;
import java.util.List;

/** 定时 HTTP 任务 Service API */
public interface SysScheduledTaskApi {

    /**
     * 创建定时任务。
     *
     * <p>任务保存成功后，会通过 {@code TaskScheduler} 在计划执行时间触发一次 HTTP 调用。
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param req 创建请求
     * @return 任务 ID
     */
    Long createTask(String projectNo, Long subjectId, CreateScheduledTaskReq req);

    /**
     * 根据任务 ID 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    ScheduledTaskResp getTaskById(Long id);

    /**
     * 按状态查询任务列表。
     *
     * @param status 任务状态，可为 {@code null} 表示查询全部
     * @return 任务列表
     */
    List<ScheduledTaskResp> listTasksByStatus(Integer status);

    /**
     * 立即执行任务（跳过定时调度，用于测试）。
     *
     * @param id 任务 ID
     * @return 是否已触发
     */
    boolean executeNow(Long id);
}
