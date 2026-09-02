package com.jdec.platform.data.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 抽象动态数据聚合基类 (读写同构的核心数据与递归树载体)
 *
 * @param <T> 子模块自身的递归类型 (如 DynamicDetailResp 或 DynamicSaveReq)
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "动态数据聚合抽象基类")
public abstract class AbstractDynamicData<T extends AbstractDynamicData<T>>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(
            description = "当前操作的模块 ID",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "101")
    private Long moduleId;

    @Schema(description = "单条业务记录的物理表数据集 (对齐查询返回的单条 record，Key 为表名)")
    private Map<String, Object> record;

    @Schema(description = "多条业务记录的物理表数据集列表 (对齐查询返回的分页 records 列表，每个元素为以表名为 Key 的 Table-Map)")
    private List<Map<String, Object>> records;

    @Schema(description = "兼容保留: 物理表数据集 (功能等同于 record)")
    private Map<String, Object> tables;

    @Schema(description = "挂载在当前模块下的子模块列表 (标准递归树形结构)")
    private List<T> subModules;

    /** 统一获取所有待持久化的 Table-Map 记录列表 (支持 records、record 与旧版 tables 自由输入) */
    public List<Map<String, Object>> getEffectiveRecords() {
        if (records != null && !records.isEmpty()) {
            return records;
        }
        if (record != null && !record.isEmpty()) {
            return List.of(record);
        }
        if (tables != null && !tables.isEmpty()) {
            return List.of(tables);
        }
        return Collections.emptyList();
    }

    /** 统一获取单条 Table-Map 数据集 (用于单主记录模块快捷访问) */
    public Map<String, Object> getEffectiveSingleRecord() {
        if (record != null && !record.isEmpty()) {
            return record;
        }
        if (tables != null && !tables.isEmpty()) {
            return tables;
        }
        if (records != null && !records.isEmpty()) {
            return records.get(0);
        }
        return Collections.emptyMap();
    }
}
