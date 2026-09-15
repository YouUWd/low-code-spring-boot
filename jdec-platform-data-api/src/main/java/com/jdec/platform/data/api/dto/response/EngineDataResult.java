package com.jdec.platform.data.api.dto.response;

import com.jdec.platform.data.api.dto.model.SysModuleInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 引擎统一数据与元数据响应容器 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "引擎统一响应容器（携带元数据与物理数据）")
public class EngineDataResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "系统模块信息")
    private SysModuleInfo meta;

    @Schema(description = "物理数据载荷")
    private T data;

    public static <T> EngineDataResult<T> of(SysModuleInfo meta, T data) {
        return new EngineDataResult<>(meta, data);
    }
}
