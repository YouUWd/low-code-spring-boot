package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 多模块同构原子批量保存请求模型 (模块对等扁平化，保存顺序由元数据驱动推导) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "多模块同构原子批量保存请求模型")
public class BatchDynamicSaveReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "待保存的模块列表 (无需人为区分主子层级，由后端元数据推导依赖拓扑并有序执行)")
    private List<DynamicSaveReq> modules;
}
