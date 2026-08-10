package com.jdec.platform.common_business.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jdec.platform.common_business.api.dto.common.DataAuditContentDTO;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OpLogResp implements Serializable {

    private String actionName;

    private String actionPath;

    private String actionTypeTitle;

    private String actionIcon;

    private UserResp createdUser;

    private UserResp realUser;

    private DataAuditContentDTO description;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdDate;
}
