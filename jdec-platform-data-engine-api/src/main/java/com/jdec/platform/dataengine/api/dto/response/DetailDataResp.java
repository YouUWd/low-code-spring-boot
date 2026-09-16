package com.jdec.platform.dataengine.api.dto.response;

import com.jdec.platform.dataengine.api.dto.model.SysModuleInfo;
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
    private SysModuleInfo meta;

    private T data;

    public static <T> DetailDataResp<T> of(SysModuleInfo meta, T data) {
        return new DetailDataResp<>(meta, data);
    }
}
