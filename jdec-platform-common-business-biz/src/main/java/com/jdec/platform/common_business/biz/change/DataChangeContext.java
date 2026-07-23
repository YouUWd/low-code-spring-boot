package com.jdec.platform.common_business.biz.change;

import com.jdec.platform.common_business.api.dto.common.ModuleFilterInfo;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataChangeContext implements Serializable {

    private Long moduleId;

    private String tableName;

    private Object before;

    private Object after;

    private ModuleFilterInfo moduleFilterInfo;
}
