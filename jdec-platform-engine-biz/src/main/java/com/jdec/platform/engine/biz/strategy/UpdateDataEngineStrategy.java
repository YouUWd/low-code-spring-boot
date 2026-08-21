package com.jdec.platform.engine.biz.strategy;

import com.jdec.platform.config.api.dto.response.PermissionFieldInfo;
import com.jdec.platform.config.api.dto.response.PermissionTableGroupResp;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.engine.api.dto.request.DataUpdateReq;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.UpdateSetFirstStep;
import org.jooq.UpdateSetMoreStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component("updateDataEngineStrategy")
public class UpdateDataEngineStrategy extends AbstractDataEngineStrategy<DataUpdateReq, Void> {

    private static final ThreadLocal<Long> CURRENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> UPDATABLE_FIELDS = new ThreadLocal<>();

    public Void executeWithId(String moduleCode, Long id, DataUpdateReq request) {
        try {
            CURRENT_ID.set(id);
            return execute(moduleCode, request);
        } finally {
            CURRENT_ID.remove();
            UPDATABLE_FIELDS.remove();
        }
    }

    @Override
    protected void checkPermission(
            String projectNo,
            Long subjectId,
            SysModuleCompleteResp moduleCompleteResp,
            DataUpdateReq request) {
        Long userId = AppContext.getUserId();
        if (userId == null) {
            UPDATABLE_FIELDS.set(null);
            return;
        }

        Long roleId = 1L; // TODO: 动态获取

        List<PermissionTableGroupResp> permissions =
                sysModuleFieldPermissionApi.getRoleModuleFieldPermissions(
                        projectNo, subjectId, roleId, moduleCompleteResp.getModule().getId());

        String primaryTable = moduleCompleteResp.getModule().getPrimaryTable();

        List<String> updatableColumnNames = new ArrayList<>();
        if (permissions != null) {
            for (PermissionTableGroupResp group : permissions) {
                if (group.getTableName().equals(primaryTable)) {
                    if (group.getUpdatableFields() != null) {
                        updatableColumnNames =
                                group.getUpdatableFields().stream()
                                        .map(PermissionFieldInfo::getFieldCode)
                                        .collect(Collectors.toList());
                    }
                }
            }
        }

        // 校验请求的字段是否在可更新字段列表中
        if (!updatableColumnNames.isEmpty()) {
            for (String key : request.getData().keySet()) {
                if (!updatableColumnNames.contains(key) && !key.equals("id")) {
                    throw new BusinessException("无权限更新字段: " + key);
                }
            }
        }

        UPDATABLE_FIELDS.set(updatableColumnNames);
    }

    @Override
    protected Void doExecute(SysModuleCompleteResp moduleCompleteResp, DataUpdateReq request) {
        String projectNo = AppContext.getProjectNo();
        String primaryTable = moduleCompleteResp.getModule().getPrimaryTable();
        Long id = CURRENT_ID.get();

        DSLContext dsl = dataSourceResolver.getDSLContext(projectNo);

        UpdateSetFirstStep<Record> updateStep = dsl.update(DSL.table(primaryTable));

        if (request.getData().isEmpty()) {
            return null;
        }

        UpdateSetMoreStep<Record> setMoreStep = null;
        boolean first = true;
        for (Map.Entry<String, Object> entry : request.getData().entrySet()) {
            if (first) {
                setMoreStep = updateStep.set(DSL.field(entry.getKey()), entry.getValue());
                first = false;
            } else {
                setMoreStep = setMoreStep.set(DSL.field(entry.getKey()), entry.getValue());
            }
        }

        if (setMoreStep != null) {
            setMoreStep.where(DSL.field("id").eq(id)).execute();
        }

        return null;
    }
}
