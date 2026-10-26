package com.jdec.platform.dataengine.biz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "权限信息返回数据模型")
public class RightInfoVO {

    @Schema(description = "字段权限信息")
    private List<FormField> formField;

    @Schema(description = "模块按钮信息，供判断当前模块按钮状态及页面模式、保存按钮显隐等。")
    private Set<FormButton> btnList;

    @Schema(description = "模块对应的菜单ID")
    private Long menuId;

    @Schema(description = "模块对应的互动权限")
    private Set<InterOperateRight> interOperateNode;
}
