package com.jdec.platform.data.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jdec.platform.data.api.dto.common.DataBlock;
import com.jdec.platform.data.api.dto.common.DataRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存/更新同构响应模型 (SaveResp)
 *
 * <p>包含根模块生成的主键 ID 以及回填了各级新生成主键后的完整业务数据区块 (与 DataBlock 100% 同构)。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "保存/更新同构响应模型")
public class SaveResp implements Serializable {

    @Schema(description = "根模块生成/更新的主键 ID", example = "1001")
    private Long rootId;

    @Schema(description = "写入成功后回填了各级新生成主键 ID 的完整数据区块 (与 DataBlock 100% 同构)")
    private DataBlock data;

    public static SaveResp of(Long rootId, DataBlock data) {
        return SaveResp.builder().rootId(rootId).data(data).build();
    }

    /** 快捷获取根实体主记录首行 */
    @JsonIgnore
    public DataRow getFirstRecord() {
        return data != null ? data.getFirstRecord() : null;
    }
}
