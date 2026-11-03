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
 * 公司层级树节点响应对象
 *
 * @author JDEC Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "公司层级树节点响应对象")
public class CompanyTreeNodeResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "单据物理主键ID")
    private Long id;

    @Schema(description = "业务编号")
    private String businessNo;

    @Schema(description = "版本号")
    private Integer versionNo;

    @Schema(description = "外部对象全称")
    private String companyName;

    @Schema(description = "业务状态(99:已启用, 1/3:草稿/审批中等)")
    private Integer businessStatus;

    @Schema(description = "登记状态")
    private String regStatus;

    @Schema(description = "对象性质")
    private String nature;

    @Schema(description = "是否为当前查看的单据节点")
    private Boolean isCurrent;

    @Schema(description = "父级原始关联信息(JSON字符串)")
    private String parentId;

    @Schema(description = "子企业列表")
    @Builder.Default
    private List<CompanyTreeNodeResp> children = new ArrayList<>();
}
