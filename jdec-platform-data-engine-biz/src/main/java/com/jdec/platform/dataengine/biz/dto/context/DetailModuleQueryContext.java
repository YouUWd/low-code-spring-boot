package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
public class DetailModuleQueryContext extends DetailQueryContext {

    private ModuleFilterInfo moduleFilterInfo;

    private Long moduleId;
}
