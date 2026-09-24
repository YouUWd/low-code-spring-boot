package com.jdec.platform.data.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 纯粹单条/实体详情数据模型 (支持 100% 树形同构与 records 列表，结构与 DataPage 保持一致，无分页属性) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "单条/实体详情数据响应")
public class DataDetail<T> implements Serializable {

    @Schema(description = "模块 ID", example = "1001")
    private Long moduleId;

    @Schema(description = "数据记录列表 (自相似同构树)")
    private List<T> records;

    public static <T> DataDetail<T> of(Long moduleId, List<T> records) {
        return DataDetail.<T>builder().moduleId(moduleId).records(records).build();
    }

    public static <T> DataDetail<T> empty(Long moduleId) {
        return DataDetail.<T>builder().moduleId(moduleId).records(Collections.emptyList()).build();
    }
}
