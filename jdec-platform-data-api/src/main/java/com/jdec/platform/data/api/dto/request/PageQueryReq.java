package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/** 业务数据动态分页查询请求模型 (PageQueryReq) */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema(description = "业务数据动态分页查询请求模型")
public class PageQueryReq extends QueryReq {

    @Schema(description = "当前页码 (从 1 开始，默认 1)", example = "1")
    private Integer pageNo;

    @Schema(description = "每页大小 (默认 20)", example = "20")
    private Integer pageSize;
}
