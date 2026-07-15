package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

@Data
@Schema(description = "排序项")
public class OrderItemReq implements Serializable {

    @Schema(description = "表名")
    private String tableName;

    @Schema(description = "字段")
    private String field;

    @Schema(description = "排序方式")
    private String value;
}
