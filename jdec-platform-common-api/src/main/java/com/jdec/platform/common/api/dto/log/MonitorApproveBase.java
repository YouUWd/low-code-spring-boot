package com.jdec.platform.common.api.dto.log;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.jdec.platform.shared.constant.MonitorLogConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 监控日志审批信息基类（monitor_log.approve）
 *
 * <p>承载各类型日志公用的审批字段，类型独有的字段由子类声明；落库前由后端序列化为 JSON 字符串写入 approve 列（实体侧为字符串类型），查询时再反序列化为对象返回前端。
 *
 * <p>子类由 JSON 内的 {@code logSubType} 标识决定：{@code operationLog}-操作日志；未传时按基类处理。
 */
@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "logSubType",
        visible = true,
        defaultImpl = MonitorApproveBase.class)
@JsonSubTypes({
    @JsonSubTypes.Type(
            value = MonitorApproveOperation.class,
            name = MonitorLogConstant.OPERATION_LOG)
})
@Schema(description = "监控日志审批信息（基类：共用字段）")
public class MonitorApproveBase implements Serializable {

    @Schema(description = "日志子类型，操作日志传 operationLog，其它类型可不传")
    private String logSubType;

    @Schema(description = "审批动作名称", example = "提交审批")
    private String name;

    @Schema(description = "审批动作图标", example = "icon-submit")
    private String path;

    @Schema(description = "审批类型，前端根据类型决定审批结果字体颜色", example = "cancel")
    private String type;

    @Schema(description = "审批结果", example = "通过")
    private String result;
}
