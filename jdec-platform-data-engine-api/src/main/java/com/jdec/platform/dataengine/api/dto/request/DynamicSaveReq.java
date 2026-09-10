package com.jdec.platform.dataengine.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.TabTreeNodeDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "动态主子表保存请求模型 (支持前端原封不动提交详情返回的对象)")
public class DynamicSaveReq {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块嵌套数据")
    private NestedModuleDataDTO nestedModuleData;

    @Schema(description = "表关联关系树")
    private TabTreeNodeDTO root;

    @Schema(description = "按钮类型")
    private String btnType;
}
