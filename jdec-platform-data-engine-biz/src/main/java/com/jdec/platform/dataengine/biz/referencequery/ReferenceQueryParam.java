package com.jdec.platform.dataengine.biz.referencequery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 引用查询参数模型父类对象 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "数据引用查询参数模型对象")
public class ReferenceQueryParam {

    private String tableName;

    private String fieldName;

    @Schema(description = "业务类型(词云或内部引用)")
    private String bizType;
}
