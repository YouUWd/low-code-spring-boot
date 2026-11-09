package com.jdec.platform.data.api.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自相似视图数据区块 (DataBlock)
 *
 * <p>数据引擎读写同构的核心载体：
 *
 * <ul>
 *   <li>1. 详情查询 (getDetail) 的出参实体；
 *   <li>2. 保存更新 (save) 的入参实体 (读写 100% 同源强约束)；
 *   <li>3. 行记录 (DataRow) 内部自相似挂载的子视图区块 (children)。
 * </ul>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "自相似视图数据区块 (读写同构核心实体)")
public class DataBlock implements Serializable {

    @Schema(description = "业务视图编码", example = "COMPANY")
    @JsonProperty("view")
    private String view;

    @Schema(description = "总记录数 (分页时呈现，全量时为 null)", example = "100")
    @JsonProperty("total")
    private Long total;

    @Schema(description = "区块内的行记录列表")
    @JsonProperty("records")
    private List<DataRow> records;

    // ==================== 便捷工厂 ====================

    /** 便捷工厂：根据视图编码与多行记录构建 */
    public static DataBlock of(String view, List<DataRow> records) {
        return DataBlock.builder().view(view).records(records).build();
    }

    /** 便捷工厂：根据视图编码、多行记录与总条数构建 */
    public static DataBlock of(String view, List<DataRow> records, Long total) {
        return DataBlock.builder().view(view).records(records).total(total).build();
    }

    /** 便捷工厂：根据视图编码与单行记录构建 */
    public static DataBlock of(String view, DataRow singleRecord) {
        return DataBlock.builder()
                .view(view)
                .records(singleRecord != null ? List.of(singleRecord) : Collections.emptyList())
                .build();
    }

    /** 空区块工厂 (带视图编码) */
    public static DataBlock empty(String view) {
        return DataBlock.builder().view(view).records(Collections.emptyList()).build();
    }

    // ==================== 实用辅助方法 ====================

    /** 获取首条单据主记录 (单据详情场景便捷访问) */
    @JsonIgnore
    public DataRow getFirstRecord() {
        return (records != null && !records.isEmpty()) ? records.get(0) : null;
    }

    /** 区块内记录行数 */
    public int size() {
        return records == null ? 0 : records.size();
    }

    /** 是否无数据行 */
    @JsonIgnore
    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }

    /** 是否包含数据行 */
    @JsonIgnore
    public boolean hasRecords() {
        return !isEmpty();
    }

    /** 追加数据行 */
    public DataBlock addRecord(DataRow row) {
        if (row == null) {
            return this;
        }
        if (this.records == null) {
            this.records = new java.util.ArrayList<>();
        } else if (!(this.records instanceof java.util.ArrayList)) {
            this.records = new java.util.ArrayList<>(this.records);
        }
        this.records.add(row);
        return this;
    }
}
