package com.jdec.platform.dataengine.biz.dsl;

import com.jdec.platform.config.api.dto.common.ModuleTableDTO;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** jOOQ 多表 Join 组装器 */
@Component
public class JooqSqlBuilder {

    /** 构建包含主表与 1:1/N:1 关联从表的 SelectJoinStep */
    public SelectJoinStep<Record> buildSelectFrom(
            DSLContext dsl, SysModuleCompleteResp completeResp) {
        String primaryTable = completeResp.getModule().getPrimaryTable();
        Table<?> fromTable = DSL.table(DSL.name(primaryTable));
        SelectJoinStep<Record> query = dsl.select(DSL.asterisk()).from(fromTable);

        List<ModuleTableDTO> moduleTables = completeResp.getModuleTables();
        if (moduleTables != null) {
            for (ModuleTableDTO tableDto : moduleTables) {
                String relationType = tableDto.getRelationType();
                // 仅对 1:1 或 N:1 从表进行物理 Join，1:N 从表由二阶段查询处理
                if ("1:1".equalsIgnoreCase(relationType)
                        || "N:1".equalsIgnoreCase(relationType)
                        || "ONE_TO_ONE".equalsIgnoreCase(relationType)) {
                    String joinTableName = tableDto.getTableName();
                    String leftField = tableDto.getJoinLeftField();
                    String rightField = tableDto.getJoinRightField();

                    query =
                            query.leftJoin(DSL.table(DSL.name(joinTableName)))
                                    .on(
                                            DSL.field(DSL.name(primaryTable, leftField))
                                                    .eq(
                                                            DSL.field(
                                                                    DSL.name(
                                                                            joinTableName,
                                                                            rightField))));
                }
            }
        }

        return query;
    }
}
