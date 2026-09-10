package com.jdec.platform.data.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jdec.platform.config.api.dto.response.ModuleHeaderNodeDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 纯粹分页数据模型 (支持 100% 树形同构 Header 与同级 records) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "分页数据响应")
public class DataPage<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数", example = "20")
    private Integer pageSize;

    @Schema(description = "总记录数", example = "100")
    private Long total;

    @Schema(description = "本次查询精准对齐的树形表头契约")
    private ModuleHeaderNodeDTO header;

    @Schema(description = "数据记录列表")
    private List<T> records;

    public static <T> DataPage<T> of(
            Integer pageNo, Integer pageSize, Long total, List<T> records) {
        return DataPage.<T>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(records)
                .build();
    }

    public static <T> DataPage<T> empty(Integer pageNo, Integer pageSize) {
        return DataPage.<T>builder()
                .pageNo(pageNo != null ? pageNo : 1)
                .pageSize(pageSize != null ? pageSize : 20)
                .total(0L)
                .records(Collections.emptyList())
                .build();
    }
}
