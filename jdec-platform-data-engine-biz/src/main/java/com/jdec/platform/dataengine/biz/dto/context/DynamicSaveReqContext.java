package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.api.dto.request.DynamicSaveReq;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DynamicSaveReqContext implements Serializable {

    // 当前模块的请求数据
    private DynamicSaveReq req;

    // 父模块ID
    private Long parentModuleId;

    // 父模块主表ID
    private Long parentModulePrimaryTableId;
}
