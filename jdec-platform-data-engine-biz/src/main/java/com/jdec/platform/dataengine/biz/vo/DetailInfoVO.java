package com.jdec.platform.dataengine.biz.vo;

import com.jdec.platform.dataengine.biz.dto.FormButton;
import com.jdec.platform.dataengine.biz.dto.FormField;
import com.jdec.platform.dataengine.biz.dto.InterOperateRight;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** 详情接口单个模块返回信息类 */
@Data
@Builder
public class DetailInfoVO {

    @Schema(description = "业务表数据字段")
    private Map<String, Object> data;

    @Schema(description = "字段权限信息")
    private List<FormField> formFieldList;

    @Schema(description = "模块按钮集合，列表模块行操作按钮、申请按钮等")
    private List<FormButton> btnList;

    @Schema(description = "互动权限集合")
    private List<InterOperateRight> interOperateNode;

    /** 审批链版本对比信息 */
    @Schema(description = "版本对比信息")
    private VersionCompareInfo formChangeList;
}
