package com.jdec.platform.common.biz.monitorlog.handler;

import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.common.biz.monitorlog.entity.MonitorLog;

/**
 * 监控日志保存处理器
 *
 * <p>按日志类型拆分保存逻辑的扩展点：各类日志当前都只做「请求对象转 JSON 字符串落库」，后续某类日志需要额外处理（补全字段、 联动写入等）时，新增一个实现并在 {@link
 * #supportType()} 返回对应日志类型即可，保存入口无需改动。
 */
public interface MonitorLogSaveHandler {

    /** 该处理器支撑的日志类型（0-操作日志，1-消息日志，2-系统日志） */
    Integer supportType();

    /** 将保存请求转换为待落库的日志实体 */
    MonitorLog buildEntity(MonitorLogSaveReq req);
}
