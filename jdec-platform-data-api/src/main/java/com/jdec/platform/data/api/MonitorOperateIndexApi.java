package com.jdec.platform.data.api;

import com.jdec.platform.data.api.dto.request.MonitorOperateIndexSaveReq;
import com.jdec.platform.data.api.dto.response.MonitorOperateIndexResp;
import java.util.List;

/**
 * 操作日志索引 API 接口
 *
 * <p>{@code monitor_operate_index} 表位于项目业务库，按当前请求上下文的项目编码（{@code X-Project-No} 请求头）动态切换数据源。
 */
public interface MonitorOperateIndexApi {

    /**
     * 保存操作日志索引
     *
     * @param req 操作日志索引保存请求
     */
    void saveIndex(MonitorOperateIndexSaveReq req);

    /**
     * 按模块 + 单据查询操作日志索引
     *
     * @param moduleId 模块id
     * @param dataId 单据id
     * @return 操作日志索引列表
     */
    List<MonitorOperateIndexResp> listByData(Long moduleId, Long dataId);
}
