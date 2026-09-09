package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 动态主子表同构原子保存请求模型 (自相似模块保存树)
 *
 * <p>与查询接口响应结构 100% 读写同构：
 *
 * <ul>
 *   <li>每个节点代表一个业务模块 (moduleId)；
 *   <li>模块下的数据统一通过 records (List&lt;Map&gt;) 表达；
 *   <li>单条保存即为长度为 1 的 records 列表；
 *   <li>子模块既可以在 records 各行内部通过 children 挂载 (行内自相似嵌套，严密对齐 query response)，也可以在节点级通过 children 挂载。
 * </ul>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "动态主子表同构原子保存请求模型 (自相似模块保存树)")
public class DynamicSaveReq extends DynamicTreeNode<DynamicSaveReq> {

    private static final long serialVersionUID = 1L;

    @Schema(
            description = "待持久化的业务记录列表 (单条主记录传包含 1 个元素的列表，多条明细传多条。每条记录内可通过 children 挂载行内自相似子模块)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Map<String, Object>> records;

    /** 便捷构建单条记录语法糖 */
    public void setRecord(Map<String, Object> record) {
        if (record != null) {
            this.records = List.of(record);
        } else {
            this.records = null;
        }
    }
}
