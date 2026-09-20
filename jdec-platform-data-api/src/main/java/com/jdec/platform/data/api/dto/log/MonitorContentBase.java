package com.jdec.platform.data.api.dto.log;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.jdec.platform.shared.constant.MonitorLogConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 监控日志内容基类（monitor_log.content）
 *
 * <p>承载各类型日志公用的内容字段，类型独有的字段由子类声明；落库前由后端序列化为 JSON 字符串写入 content 列（实体侧为字符串类型），查询时再反序列化为对象返回前端。
 *
 * <p>子类由 JSON 内的 {@code logSubType} 标识决定：{@code operationLog}-操作日志，{@code messageLog}-消息日志， {@code
 * systemLog}-系统日志；未传时按基类处理。
 */
@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "logSubType",
        visible = true,
        defaultImpl = MonitorContentBase.class)
@JsonSubTypes({
    @JsonSubTypes.Type(
            value = MonitorContentOperation.class,
            name = MonitorLogConstant.OPERATION_LOG),
    @JsonSubTypes.Type(value = MonitorContentMessage.class, name = MonitorLogConstant.MESSAGE_LOG),
    @JsonSubTypes.Type(value = MonitorContentSystem.class, name = MonitorLogConstant.SYSTEM_LOG)
})
@Schema(description = "监控日志内容（基类：共用字段）")
public class MonitorContentBase implements Serializable {

    @Schema(description = "日志子类型：operationLog-操作日志，messageLog-消息日志，systemLog-系统日志")
    private String logSubType;

    @Schema(description = "模块名称", example = "外部对象")
    private String moduleName;

    @Schema(description = "链接文本", example = "外部对象列表")
    private String linkName;

    @Schema(description = "单据版本号", example = "1")
    private Integer version;

    @Schema(description = "操作内容", example = "提交")
    private String operation;
}
