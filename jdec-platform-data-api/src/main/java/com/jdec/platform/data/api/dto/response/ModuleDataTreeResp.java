package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 模块树形数据集响应模型 (已过时，推荐使用 {@link MenuDataResp}) */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Schema(description = "模块树形数据集 (已过时，推荐使用 MenuDataResp)")
@Deprecated
public class ModuleDataTreeResp extends MenuDataResp {}
