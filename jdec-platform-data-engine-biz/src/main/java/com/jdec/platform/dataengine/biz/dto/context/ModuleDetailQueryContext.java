package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.shared.security.model.LoginUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModuleDetailQueryContext {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "数据ID")
    private Long dataId;

    @Schema(description = "版本号")
    private Integer versionNo;

    Map<Long, SysModuleCombineInfo> moduleCacheMap;

    private String projectNo;

    private Long subjectId;

    private LoginUser loginUser;
}
