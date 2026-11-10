package com.jdec.platform.externalobject.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公司人员构成架构树节点响应对象
 *
 * @author JDEC Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公司人员构成架构树节点响应对象")
public class MemberTreeNodeResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "人员业务唯一标识号")
    private String businessNo;

    @Schema(description = "单据快照版本号")
    private Integer versionNo;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "所属公司ID（关联 info.id）")
    private Long companyId;

    @Schema(description = "节点类型（company:企业/公司, department:部门, employee:员工）")
    private String type;

    @Schema(description = "成员名称")
    private String memberName;

    @Schema(description = "部门名称或员工姓名")
    private String name;

    @Schema(description = "外部部门号或工号")
    private String code;

    @Schema(description = "部门负责人信息（JSON对象）")
    private Object deptLeader;

    @Schema(description = "职务/岗位")
    private String position;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "展示排序序号")
    private Integer sortOrder;

    @Schema(description = "状态（JSON对象或数组）")
    private Object status;

    @Schema(description = "节点ID")
    private String nodeId;

    @Schema(description = "父节点ID")
    private String parentNodeId;

    @Schema(description = "证件类型（JSON对象或数组）")
    private Object identityType;

    @Schema(description = "证件号码")
    private String identityNo;

    @Schema(description = "移动电话")
    private String mobilePhoneNo;

    @Schema(description = "工作领域")
    private String workField;

    @Schema(description = "专业领域")
    private String expertiseField;

    @Schema(description = "照片URL")
    private String photoUrl;

    @Schema(description = "员工联系方式列表（针对员工节点）")
    @Builder.Default
    private List<MemberContactResp> contacts = new ArrayList<>();

    @Schema(description = "子节点列表（下级部门或直属员工）")
    @Builder.Default
    private List<MemberTreeNodeResp> children = new ArrayList<>();
}
