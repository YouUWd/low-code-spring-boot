package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.dto.log.MonitorApproveBase;
import com.jdec.platform.config.api.dto.log.MonitorContentBase;
import com.jdec.platform.config.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.config.api.dto.log.MonitorMockInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 监控日志保存请求
 *
 * <p>{@code approve}、{@code content} 按「基类 + 子类」声明：调用方在对象内传 {@code logSubType} 标识具体子类型（operationLog
 * / messageLog），由数据引擎按日志类型分发处理并序列化为 JSON 字符串落库。
 *
 * <p>与数据引擎 {@code MonitorLogSaveReq} 结构同构，由 {@code OpenMonitorLogApi} 的实现方完成转换。
 */
@Data
@Schema(description = "监控日志保存请求")
public class MonitorLogSaveReq implements Serializable {

    @Schema(
            description = "日志类型：0-操作日志，1-消息日志，2-系统日志",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "0")
    private Integer type;

    @Schema(description = "系统日志类型", example = "systemLog")
    private String sysType;

    @Schema(description = "主体id（调用方传，主体名称由日志侧查配置中心按此id补全）")
    private Long subjectId;

    @Schema(description = "主体名称（日志侧补全，调用方无需传）")
    private String subjectName;

    @Schema(description = "审批数据")
    private MonitorApproveBase approve;

    @Schema(description = "登录信息")
    private MonitorLoginInfo loginInfo;

    @Schema(description = "模拟信息")
    private MonitorMockInfo mockInfo;

    @Schema(description = "请求IP")
    private String ip;

    @Schema(description = "模块id")
    private Long moduleId;

    @Schema(description = "单据id")
    private Long dataId;

    @Schema(description = "浏览器信息")
    private String browser;

    @Schema(description = "平台信息")
    private String platForm;

    @Schema(description = "日志内容")
    private MonitorContentBase content;

    @Schema(description = "备注（自由结构 JSON）")
    private Object remark;

    @Schema(description = "日志记录时间，为空时取当前时间", example = "2026-09-17 10:00:00")
    private LocalDateTime recordTime;
}
