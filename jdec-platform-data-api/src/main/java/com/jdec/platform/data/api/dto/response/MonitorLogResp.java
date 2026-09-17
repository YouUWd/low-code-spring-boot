package com.jdec.platform.data.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jdec.platform.data.api.dto.log.MonitorApproveBase;
import com.jdec.platform.data.api.dto.log.MonitorContentBase;
import com.jdec.platform.data.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.data.api.dto.log.MonitorMockInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 监控日志查询响应
 *
 * <p>库中 approve / content / login_info / mock_info / remark 均为 JSON 字符串，返回前已反序列化为对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "监控日志查询响应")
public class MonitorLogResp implements Serializable {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "日志类型：0-操作日志，1-消息日志，2-系统日志", example = "0")
    private Integer type;

    @Schema(description = "系统日志类型", example = "systemLog")
    private String sysType;

    @Schema(description = "主体id")
    private Long subjectId;

    @Schema(description = "主体名称")
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

    @Schema(description = "日志记录时间", example = "2026-09-17 10:00:00")
    private LocalDateTime recordTime;

    @Schema(description = "日志保存时间", example = "2026-09-17 10:00:01")
    private LocalDateTime createdDate;
}
