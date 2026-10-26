package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 所有通用配置响应 */
@Data
@Schema(description = "所有通用配置响应")
public class AllConfigResp {

    @Schema(description = "分类英文标识")
    private String categoryAlias;

    @Schema(description = "格式：list / tree / kv")
    private String format;

    @Schema(description = "配置项列表")
    private List<ConfigItemVO> items;

    /** 配置项视图对象 */
    @Data
    @Schema(description = "配置项视图对象")
    public static class ConfigItemVO {

        @Schema(description = "主键ID")
        private Long id;

        @Schema(description = "父级ID")
        private Long pid;

        @Schema(description = "层级路径")
        private String path;

        @Schema(description = "层级深度")
        private Integer depth;

        @Schema(description = "显示名称")
        private String label;

        @Schema(description = "存储值")
        private String value;

        @Schema(description = "说明")
        private String description;

        @Schema(description = "扩展属性")
        private String extra;

        @Schema(description = "排序")
        private Integer sort;

        @Schema(description = "1启用 0禁用")
        private Integer status;

        @Schema(description = "主体ID")
        private Long subjectId;
    }
}
