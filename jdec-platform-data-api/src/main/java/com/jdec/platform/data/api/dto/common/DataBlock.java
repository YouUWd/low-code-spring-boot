package com.jdec.platform.data.api.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jdec.platform.data.api.dto.response.DataPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自相似模块数据区块 (DataBlock)
 *
 * <p>数据引擎读写同构的核心载体：
 *
 * <ul>
 *   <li>1. 详情查询 (getDetail) 的出参实体；
 *   <li>2. 保存更新 (save) 的入参实体 (读写 100% 同源强约束)；
 *   <li>3. 行记录 (DataRow) 内部自相似挂载的子区块 (children)。
 * </ul>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "自相似模块数据区块 (读写同构核心实体)")
public class DataBlock implements Serializable {

    @Schema(description = "业务模块 ID", example = "1001")
    private Long moduleId;

    @Schema(description = "区块内的行记录列表")
    private List<DataRow> records;

    /** 便捷工厂：根据模块 ID 与多行记录构建 */
    public static DataBlock of(Long moduleId, List<DataRow> records) {
        return DataBlock.builder().moduleId(moduleId).records(records).build();
    }

    /** 便捷工厂：根据模块 ID 与单行记录构建 */
    public static DataBlock of(Long moduleId, DataRow singleRecord) {
        return DataBlock.builder()
                .moduleId(moduleId)
                .records(singleRecord != null ? List.of(singleRecord) : Collections.emptyList())
                .build();
    }

    /** 空区块工厂 */
    public static DataBlock empty(Long moduleId) {
        return DataBlock.builder().moduleId(moduleId).records(Collections.emptyList()).build();
    }

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
        if (records == null) {
            records = new ArrayList<>();
        } else if (!(records instanceof ArrayList)) {
            records = new ArrayList<>(records);
        }
        records.add(row);
        return this;
    }

    /** 从首条主记录中查找指定子模块的子数据区块 */
    public DataBlock findChild(Long childModuleId) {
        DataRow first = getFirstRecord();
        return first != null ? first.getChild(childModuleId) : null;
    }

    /** 包装为标准分页响应 */
    public DataPage<DataBlock> toPage(Integer pageNo, Integer pageSize, Long total) {
        return DataPage.of(this, pageNo, pageSize, total);
    }
}
