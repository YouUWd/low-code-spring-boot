package com.jdec.platform.data.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动态主子表同构原子保存请求模型 (自相似模块保存树)
 *
 * <p>与查询接口响应结构 100% 读写同构：
 *
 * <ul>
 *   <li>每个节点代表一个业务模块 (moduleId)；
 *   <li>模块下的数据统一通过 records (List&lt;DataRow&gt;) 表达；
 *   <li>单条保存即为长度为 1 的 records 列表；
 *   <li>子模块既可以在 records 各行内部通过 children 挂载 (行内自相似嵌套，严密对齐 query response)，也可以在节点级通过 children 挂载。
 *
 * @deprecated 请使用 100% 读写同构的 {@link DataBlock} 代替
 */
@Deprecated
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "动态主子表保存请求模型 (已废弃，建议使用 DataBlock)")
public class SaveReq implements Serializable {

    @Schema(
            description = "模块 ID (可选，若不传则根据 fields 自动路由推导)",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "101")
    private Long moduleId;

    @Schema(
            description = "待持久化的业务记录列表 (单条主记录传包含 1 个元素的列表，多条明细传多条。每条记录内可通过 children 挂载行内自相似子模块)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DataRow> records;

    @Schema(description = "下级子模块树 (自相似递归嵌套，可选)")
    private List<SaveReq> children;
}
