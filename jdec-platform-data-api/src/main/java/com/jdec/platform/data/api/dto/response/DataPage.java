package com.jdec.platform.data.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 纯粹分页数据模型 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页数据响应")
public class DataPage<T> {

    @Schema(description = "当前页码", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数", example = "20")
    private Integer pageSize;

    @Schema(description = "总记录数", example = "100")
    private Long total;

    @Schema(description = "数据记录列表")
    private List<T> records;

    public static <T> DataPage<T> of(
            Integer pageNo, Integer pageSize, Long total, List<T> records) {
        return new DataPage<>(pageNo, pageSize, total, records);
    }

    public static <T> DataPage<T> empty(Integer pageNo, Integer pageSize) {
        return new DataPage<>(
                pageNo != null ? pageNo : 1,
                pageSize != null ? pageSize : 20,
                0L,
                java.util.Collections.emptyList());
    }
}
