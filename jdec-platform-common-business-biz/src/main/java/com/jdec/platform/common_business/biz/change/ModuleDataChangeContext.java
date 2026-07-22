package com.jdec.platform.common_business.biz.change;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleDataChangeContext implements Serializable {

    private Long subjectId;

    private Long mainModuleId;

    private Long dataId;

    private List<DataChangeContext> dataChangeContextList;

    private Long mockUserId;

    private Long realUserId;

    private Long roleId;

    private CallTypeEnum callType;

    private Long menuId;
}
