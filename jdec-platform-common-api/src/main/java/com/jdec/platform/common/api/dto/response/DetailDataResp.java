package com.jdec.platform.common.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "详情响应模型 (包含模块标识、元数据与物理表业务数据)")
public class DetailDataResp<T> implements Serializable {
    private ModuleMetaDTO meta;

    private T data;

    public static <T> DetailDataResp<T> of(ModuleMetaDTO meta, T data) {
        return new DetailDataResp<>(meta, data);
    }
}
