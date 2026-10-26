package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.enums.SaveType;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;
import org.jooq.DSLContext;

@Data
@Builder
public class DynamicSaveReqContext implements Serializable {

    // 当前模块的请求数据
    private NestedModuleDataDTO nestedModuleData;

    // 父模块ID
    private Long parentModuleId;

    // 父模块主表ID
    private Long parentModuleMainTableDataId;

    // 根模块ID
    private Long rootModuleId;

    // 根模块对应的主表数据ID
    private Long rootModuleMainTableDataId;

    // 根模块对应的主表数据的版本
    private Integer rootModuleMainTableDataVersion;

    // 结构上的根模块ID
    private Long structureRootModuleId;

    // 事务DSL
    private DSLContext txDsl;

    // 保存类型
    private SaveType saveType;

    private boolean isApproval;
}
