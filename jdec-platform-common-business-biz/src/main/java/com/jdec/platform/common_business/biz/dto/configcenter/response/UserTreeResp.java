package com.jdec.platform.common_business.biz.dto.configcenter.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户选择树系统设置返回对象")
public class UserTreeResp {
    @Schema(description = "部门或者用户id")
    private String id;

    @Schema(description = "标签显示名称")
    private String label;

    @Schema(description = "父节点id")
    private String pid;

    @Schema(description = "节点类型 1-部门 3-用户")
    private Integer type;

    @Schema(description = "部门id")
    private String departId;

    @Schema(description = "用户性别")
    private Integer sex;

    @Schema(description = "用户工号")
    private String workNumber;

    @Schema(description = "部门名称")
    private String departName;

    @Schema(description = "系统用户id")
    private Long sysId;

    @Schema(description = "子节点列表")
    private List<UserTreeResp> children;
}
