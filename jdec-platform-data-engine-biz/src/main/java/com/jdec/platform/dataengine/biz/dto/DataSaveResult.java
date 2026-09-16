package com.jdec.platform.dataengine.biz.dto;

import com.jdec.platform.dataengine.api.dto.model.RowChangeDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSaveResult implements java.io.Serializable {

    private Long dataId;

    private RowChangeDTO rowChange;
}
