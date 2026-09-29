package com.jdec.platform.dataengine.biz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段版本快照数据")
public class VersionSnapShot {

    private String firstSnapshot;

    private String secondSnapshot;
}
