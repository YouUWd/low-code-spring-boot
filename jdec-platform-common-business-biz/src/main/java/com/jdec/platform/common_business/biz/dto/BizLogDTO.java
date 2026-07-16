package com.jdec.platform.common_business.biz.dto;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class BizLogDTO implements Serializable {

    private String logType;

    private String projectNo;

    private String platform;

    private String browser;

    private Long loginUserId;

    private Long mockUserId;

    private Long preSubjectId;

    private Long currentSubjectId;

    private Long preRoleId;

    private Long currentRoleId;

    private String ip;
}
