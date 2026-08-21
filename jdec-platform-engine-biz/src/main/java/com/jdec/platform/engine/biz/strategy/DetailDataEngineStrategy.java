package com.jdec.platform.engine.biz.strategy;

import com.jdec.platform.config.api.dto.response.PermissionFieldInfo;
import com.jdec.platform.config.api.dto.response.PermissionTableGroupResp;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.engine.api.dto.response.DataDetailResp;
import com.jdec.platform.shared.context.AppContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectSelectStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component("detailDataEngineStrategy")
public class DetailDataEngineStrategy extends AbstractDataEngineStrategy<Long, DataDetailResp> {

    private static final ThreadLocal<List<String>> READABLE_FIELDS = new ThreadLocal<>();

    @Override
    protected void checkPermission(
            String projectNo, Long subjectId, SysModuleCompleteResp moduleCompleteResp, Long id) {
        Long userId = AppContext.getUserId();
        if (userId == null) {
            READABLE_FIELDS.set(null);
            return;
        }

        Long roleId = 1L; // TODO: 动态获取

        List<PermissionTableGroupResp> permissions =
                sysModuleFieldPermissionApi.getRoleModuleFieldPermissions(
                        projectNo, subjectId, roleId, moduleCompleteResp.getModule().getId());

        String primaryTable = moduleCompleteResp.getModule().getPrimaryTable();

        List<String> readableColumnNames = new ArrayList<>();
        if (permissions != null) {
            for (PermissionTableGroupResp group : permissions) {
                if (group.getTableName().equals(primaryTable)) {
                    if (group.getReadableFields() != null) {
                        readableColumnNames =
                                group.getReadableFields().stream()
                                        .map(PermissionFieldInfo::getFieldCode)
                                        .collect(Collectors.toList());
                    }
                }
            }
        }

        READABLE_FIELDS.set(readableColumnNames);
    }

    @Override
    protected DataDetailResp doExecute(SysModuleCompleteResp moduleCompleteResp, Long id) {
        try {
            String projectNo = AppContext.getProjectNo();
            String primaryTable = moduleCompleteResp.getModule().getPrimaryTable();

            DSLContext dsl = dataSourceResolver.getDSLContext(projectNo);

            List<String> readableFields = READABLE_FIELDS.get();
            SelectSelectStep<Record> selectStep;

            if (readableFields != null && !readableFields.isEmpty()) {
                var jooqFields =
                        readableFields.stream().map(DSL::field).collect(Collectors.toList());
                selectStep = dsl.select(jooqFields);
            } else {
                selectStep = dsl.select();
            }

            Record record =
                    selectStep
                            .from(DSL.table(primaryTable))
                            .where(DSL.field("id").eq(id))
                            .fetchOne();

            DataDetailResp resp = new DataDetailResp();
            if (record != null) {
                resp.setData(record.intoMap());
            }

            return resp;
        } finally {
            READABLE_FIELDS.remove();
        }
    }
}
