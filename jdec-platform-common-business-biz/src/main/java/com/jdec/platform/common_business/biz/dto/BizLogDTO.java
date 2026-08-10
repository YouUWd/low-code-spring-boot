package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
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

    private Long menuId;

    private Long moduleId;

    private String moduleName;

    private String dataId;
}
