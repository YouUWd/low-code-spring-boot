package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

@Data
@Schema(description = "排序项")
public class OpLogReq implements Serializable {

    private Long menuId;

    private Long dataId;
}
