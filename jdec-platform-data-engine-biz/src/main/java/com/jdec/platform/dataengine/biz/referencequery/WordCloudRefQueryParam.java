package com.jdec.platform.dataengine.biz.referencequery;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema(description = "词云最新版本查询参数模型")
public class WordCloudRefQueryParam extends ReferenceQueryParam {

    @Schema(description = "词ID集合")
    private Set<String> wordUUUIDs;

    @Schema(description = "关联关系业务编号")
    private String relationNo;

    @Schema(description = "关联关系名称")
    private String relationName;
}
