package com.jdec.platform.common.biz.dto.response;

import com.jdec.platform.data.api.dto.response.ModuleDataNode;
import com.jdec.platform.data.api.dto.response.ModuleMetaDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单复合数据详情响应模型 (携带当前模块及其所有子模块的元数据配置列表与数据载荷) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单复合数据详情响应模型")
public class MenuDetailResp implements Serializable {

    @Schema(description = "当前模块及其所有关联子模块的元数据配置列表 (包含 moduleId、menuId 以及 fields 字典)")
    private List<ModuleMetaDTO> meta;

    @Schema(description = "菜单物理数据载荷")
    private ModuleDataNode data;

    public static MenuDetailResp of(List<ModuleMetaDTO> meta, ModuleDataNode data) {
        return new MenuDetailResp(meta, data);
    }
}
