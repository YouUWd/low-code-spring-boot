package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "用户个性化设置")
public class PersonalSettingResp {
    @Schema(description = "主键id")
    private Long id;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "用户个性化列设置列表")
    private List<PersonalSettingColumn> personalSettingColumns;

    @Data
    @Schema(description = "用户个性化列设置")
    public static class PersonalSettingColumn {
        @Schema(description = "主键")
        private Long id;

        @Schema(description = "姓名")
        private String name;

        @Schema(description = "表名-英文")
        private String table;

        @Schema(description = "表名-中文")
        private String tableName;

        @Schema(description = "关联字段")
        private String field;

        @Schema(description = "宽度")
        private Integer width;

        @Schema(description = "固定")
        private String fixed;

        @Schema(description = "搜索类型")
        private String searchType;

        @Schema(description = "文本是否省略")
        private Integer ellipsis;

        @Schema(description = "可排序")
        private Integer sortable;

        @Schema(description = "排序顺序")
        private Integer sortOrder;
    }
}
