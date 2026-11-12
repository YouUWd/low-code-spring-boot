package com.jdec.platform.externalobject.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 人员联系方式响应对象
 *
 * @author JDEC Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "人员联系方式响应对象")
public class MemberContactResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "联系方式业务编号")
    private String businessNo;

    @Schema(description = "快照版本号")
    private Integer versionNo;

    @Schema(description = "主体ID")
    private Long subjectId;

    @Schema(description = "所属员工ID（关联 member.id）")
    private Long memberId;

    @Schema(description = "联系方式类型（JSON对象或数组）")
    private Object contactType;

    @Schema(description = "具体电话号码或邮箱地址")
    private String contactValue;

    @Schema(description = "是否为员工本人的默认联系方式（1:是, 0:否）")
    private Integer isPrimary;

    @Schema(description = "排序序号")
    private Integer sortOrder;
}
