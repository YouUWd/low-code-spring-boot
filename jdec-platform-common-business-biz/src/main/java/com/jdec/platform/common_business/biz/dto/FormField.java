package com.jdec.platform.common_business.biz.dto;

import com.jdec.platform.common_business.api.dto.common.FieldChainValueDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FormField {

    @Schema(description = "字段名称")
    private String fieldName;

    @Schema(description = "字段中文名")
    private String fieldTitle;

    // 字段权限 0:无权限，前端显示***;1:有查看权限;2有编辑权限
    @Schema(description = "字段权限")
    private Integer permissionCode;

    @Schema(description = "关联关系业务编号")
    private String relationBusinessNo;

    @Schema(description = "关联关系名称")
    private String relationName;

    @Schema(description = "字段表名")
    private String tableName;

    @Schema(description = "字段配置审批链的分类值")
    private List<FieldChainValueDTO> fieldChainValues;

    //    @Schema(description = "是否加密存储: 0-否, 1-是")
    //    private Integer encrypted;
    //
    //    @Schema(description = "是否生成权限节点: 0-否, 1-是")
    //    private Integer dataRightFlag;
}
