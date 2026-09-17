package com.jdec.platform.data.biz.monitorlog.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 监控日志（业务库 monitor_log 行模型，经 jOOQ 按 projectNo 动态解析数据源读写）
 *
 * <p>三种日志类型共用本表，类型独有字段以 JSON 形式落在 {@code approve}、{@code content} 等列上。
 *
 * <p>实体对应属性一律以字符串接收，JSON 与对象的转换在 DTO 与 Service 中完成。
 */
@Data
@Schema(description = "监控日志")
public class MonitorLog implements Serializable {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "日志类型：0-操作日志，1-消息日志，2-系统日志")
    private Integer type;

    @Schema(description = "系统日志类型")
    private String sysType;

    @Schema(description = "主体id")
    private Long subjectId;

    @Schema(description = "主体名称")
    private String subjectName;

    @Schema(description = "审批数据(JSON 字符串)")
    private String approve;

    @Schema(description = "登录信息(JSON 字符串)")
    private String loginInfo;

    @Schema(description = "模拟信息(JSON 字符串)")
    private String mockInfo;

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

    @Schema(description = "日志内容(JSON 字符串)")
    private String content;

    @Schema(description = "备注(JSON 字符串)")
    private String remark;

    @Schema(description = "日志记录时间")
    private LocalDateTime recordTime;

    @Schema(description = "日志保存时间")
    private LocalDateTime createdDate;
}
