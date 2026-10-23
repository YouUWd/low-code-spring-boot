package com.jdec.platform.dataengine.biz.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSaveResult implements java.io.Serializable {

    private Long dataId;

    private Integer versinNo;

    private String businessNo;

    private Integer businessStatus;
}
