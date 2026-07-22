package com.jdec.platform.common_business.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "带审批链的模块保存表单数据")
public class ApprovalModuleSaveReq {

    @Schema(description = "审批相关的操作数据")
    private ApprovalOperationReq approvalOperation;

    @Schema(description = "模块保存数据")
    List<MenuSaveReq> moduleSaveData;
}
