package com.jdec.platform.approval.biz.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class LogContentDTO implements Serializable {

    private Long moduleId;

    private String moduleName;

    private Long menuId;

    private String menuParam;

    private String hrefName;

    private String dataId;

    private String orderVersion;

    private String controlName;

    private List<Long> messageReceivers;
}
