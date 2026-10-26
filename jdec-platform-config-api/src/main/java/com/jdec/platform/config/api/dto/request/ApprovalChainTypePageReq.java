package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.shared.model.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 审批链分类分页查询请求 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "审批链分类分页查询请求")
public class ApprovalChainTypePageReq extends PageRequest {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "审批链分类ID列表（多选）")
    private List<Long> chainTypeIds;

    @Schema(description = "分类名称（模糊搜索）")
    private String title;
}
