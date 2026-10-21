package com.jdec.platform.dataengine.biz.referencequery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema(description = "内部引用最新版本查询参数模型")
public class InternalRefQueryParam extends ReferenceQueryParam {

    private String businessNo;
}
