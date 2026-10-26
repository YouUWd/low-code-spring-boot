package com.jdec.platform.hr.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 人员架构树节点 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserTreeNodeResp {

    @Schema(description = "节点ID", example = "D1")
    private String id;

    @Schema(description = "名称", example = "襄阳试界空间有限公司")
    private String label;

    @Schema(description = "父节点ID", example = "D0")
    private String pid;

    @Schema(description = "节点类型 1-部门 3-用户", example = "1")
    private Integer type;

    @Schema(description = "系统用户ID", example = "1")
    private Long sysId;

    @Schema(description = "部门ID", example = "D1")
    private String departId;

    @Schema(description = "性别", example = "1")
    private Integer sex;

    @Schema(description = "头像", example = "http://xxx.jpg")
    private String avatar;

    @Schema(description = "工号", example = "W00001")
    private String workNumber;

    @Schema(description = "部门名称", example = "襄阳试界空间有限公司")
    private String departName;

    @Schema(description = "子节点")
    private List<UserTreeNodeResp> children;
}
