package com.jdec.platform.dataengine.biz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class InterOperateRight {

    @Schema(description = "互动权限权限代码")
    private String rightCode;

    @Schema(description = "互动权限名称")
    private String rightName;
}
