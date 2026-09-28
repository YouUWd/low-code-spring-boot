package com.jdec.platform.data.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 动态数据查询通用基础请求模型 (QueryReq)
 *
 * <p>承载模块定位、字段投影、多维过滤与排序的核心通用查询能力，作为 PageQueryReq 与 DetailQueryReq 的公共基类。
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态数据查询通用基础请求模型")
public class QueryReq implements Serializable {

    @NotNull(message = "查询主模块 ID 不能为空")
    @Schema(description = "根主模块 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "101")
    private Long moduleId;

    @Schema(
            description =
                    "显式投影字段 ID 列表 (sys_module_field.id，限制为模块本身及子模块的字段)。为 null 或空时默认投影当前模块全部可见字段")
    private List<Long> fields;

    @Schema(description = "平铺过滤条件列表 (fieldId 必须属于当前模块或其子模块)")
    private List<FilterItem> filters;

    @Schema(description = "平铺排序规则列表 (fieldId 必须属于当前模块或其子模块)")
    private List<SortItem> sorts;

    /** 追加过滤条件 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T filter(FilterItem filter) {
        if (filter != null) {
            if (this.filters == null) {
                this.filters = new ArrayList<>();
            } else if (!(this.filters instanceof ArrayList)) {
                this.filters = new ArrayList<>(this.filters);
            }
            this.filters.add(filter);
        }
        return (T) this;
    }

    /** 追加排序规则 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T sort(SortItem sort) {
        if (sort != null) {
            if (this.sorts == null) {
                this.sorts = new ArrayList<>();
            } else if (!(this.sorts instanceof ArrayList)) {
                this.sorts = new ArrayList<>(this.sorts);
            }
            this.sorts.add(sort);
        }
        return (T) this;
    }

    /** 追加显式投影字段 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T field(Long fieldId) {
        if (fieldId != null) {
            if (this.fields == null) {
                this.fields = new ArrayList<>();
            } else if (!(this.fields instanceof ArrayList)) {
                this.fields = new ArrayList<>(this.fields);
            }
            this.fields.add(fieldId);
        }
        return (T) this;
    }

    /** 批量追加显式投影字段 */
    @SuppressWarnings("unchecked")
    public <T extends QueryReq> T fields(Long... fieldIds) {
        if (fieldIds != null) {
            for (Long fid : fieldIds) {
                field(fid);
            }
        }
        return (T) this;
    }
}
