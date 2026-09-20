package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 复合数据详情响应容器 (已过时，推荐使用 {@link MenuDetailResp}) */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Schema(description = "复合数据详情响应容器 (已过时，推荐使用 MenuDetailResp)")
@Deprecated
public class DataCompositeDetailResp extends MenuDetailResp {}
