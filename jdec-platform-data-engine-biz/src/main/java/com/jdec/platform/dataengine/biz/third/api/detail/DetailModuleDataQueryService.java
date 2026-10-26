package com.jdec.platform.dataengine.biz.third.api.detail;

import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DetailModuleQueryContext;
import java.util.List;

/** 查询 拼装SQL 查询详细 数据 */
public interface DetailModuleDataQueryService {

    List<RecordDataDTO> queryDetailModuleData(DetailModuleQueryContext context);
}
