package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class BizLogDTO implements Serializable {

    private String projectNo;

    private String token;

    private String logType;

    private String platform;

    private String browser;

    private String ip;

    private String origin;

    private Long loginUserId;

    private Long mockUserId;

    private Long subjectId;

    private Long roleId;

    private String logRemark;

    private String logContent;

    private Long menuId;

    private Long moduleId;

    private String moduleName;

    private String dataId;

    // 消息日志使用
    private String hrefName;

    private String orderVersion;

    private String controlName;

    private List<Long> messageReceivers;
}
