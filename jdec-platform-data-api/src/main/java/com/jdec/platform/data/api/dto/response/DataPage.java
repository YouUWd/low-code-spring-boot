package com.jdec.platform.data.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import com.jdec.platform.data.api.dto.request.PageQueryReq;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用分页数据响应模型 (DataPage)
 *
 * <p>正交分离的分页容器包装模型：承载分页控制属性 (pageNo, pageSize, total) 以及泛型实体数据 (data)。
 *
 * @param <T> 数据载体类型 (在低代码数据引擎中为 {@link DataBlock})
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "通用分页数据响应")
public class DataPage<T> implements Serializable {

    @Schema(description = "当前页码", example = "1")
    private Integer pageNo;

    @Schema(description = "每页条数", example = "20")
    private Integer pageSize;

    @Schema(description = "总记录数", example = "100")
    private Long total;

    @Schema(description = "分页数据载体")
    private T data;

    public static <T> DataPage<T> of(Integer pageNo, Integer pageSize, Long total, T data) {
        return DataPage.<T>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .data(data)
                .build();
    }

    public static DataPage<DataBlock> of(
            DataBlock block, Integer pageNo, Integer pageSize, Long total) {
        return DataPage.<DataBlock>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .data(block)
                .build();
    }

    public static <T> DataPage<T> empty(Integer pageNo, Integer pageSize) {
        return DataPage.<T>builder()
                .pageNo(pageNo != null ? pageNo : 1)
                .pageSize(pageSize != null ? pageSize : 20)
                .total(0L)
                .data(null)
                .build();
    }

    public static DataPage<DataBlock> empty(Long moduleId, Integer pageNo, Integer pageSize) {
        return DataPage.<DataBlock>builder()
                .pageNo(pageNo != null ? pageNo : 1)
                .pageSize(pageSize != null ? pageSize : 20)
                .total(0L)
                .data(DataBlock.empty(moduleId))
                .build();
    }

    public static DataPage<DataBlock> empty(PageQueryReq req) {
        if (req == null) {
            return empty(null, 1, 20);
        }
        return empty(req.getModuleId(), req.getPageNo(), req.getPageSize());
    }

    public static <T> DataPage<T> empty() {
        return empty(1, 20);
    }

    /** 函数式转换分页数据内容类型 */
    public <R> DataPage<R> map(Function<? super T, ? extends R> mapper) {
        R mappedData = this.data != null ? mapper.apply(this.data) : null;
        return DataPage.<R>builder()
                .pageNo(this.pageNo)
                .pageSize(this.pageSize)
                .total(this.total)
                .data(mappedData)
                .build();
    }

    /** 是否无数据 */
    @JsonIgnore
    public boolean isEmpty() {
        if (data == null) {
            return true;
        }
        if (data instanceof DataBlock block) {
            return block.isEmpty();
        }
        if (data instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    /** 是否包含数据 */
    @JsonIgnore
    public boolean hasRecords() {
        return !isEmpty();
    }

    /** 快捷获取 DataBlock 内的行记录列表 (当 data 为 DataBlock 或 List 时) */
    @JsonIgnore
    @SuppressWarnings("unchecked")
    public List<DataRow> getRecords() {
        if (data instanceof DataBlock block) {
            return block.getRecords() != null ? block.getRecords() : Collections.emptyList();
        }
        if (data instanceof List<?>) {
            return (List<DataRow>) data;
        }
        return Collections.emptyList();
    }

    /** 快捷获取根模块 ID (当 data 为 DataBlock 时) */
    @JsonIgnore
    public Long getModuleId() {
        if (data instanceof DataBlock block) {
            return block.getModuleId();
        }
        return null;
    }

    /** 一键转换为 DataBlock 实体 */
    @JsonIgnore
    public DataBlock toDataBlock() {
        if (data instanceof DataBlock block) {
            return block;
        }
        return null;
    }
}
