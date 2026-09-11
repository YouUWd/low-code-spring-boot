package com.jdec.platform.dataengine.biz.dto.context;

import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.dataengine.biz.dto.ModuleFilterInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.jooq.DSLContext;

@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@Data
public class StatementContext extends ListQueryContext implements Serializable {

    private SysModuleResp sysModuleCompleteResp;

    @Schema(description = "主模块主表的数据ID，查询子模块的列表")
    private Long mainDataId;

    @Schema(description = "数据源上下文对象")
    private DSLContext dsl;

    @Schema(description = "详情查看数据id")
    private Long id;

    @Schema(description = "模块字段权限数据")
    private ModuleFilterInfo moduleFilterInfo;

    @Schema(description = "列表数据对应的审批链信息")
    private List<Map<String, Object>> approvalList;

    private String tableName;

    @Schema(description = "关联外键id集合")
    private List<Long> relateIds;
}
