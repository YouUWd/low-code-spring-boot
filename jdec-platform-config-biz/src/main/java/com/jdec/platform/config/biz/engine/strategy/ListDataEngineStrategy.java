package com.jdec.platform.config.biz.engine.strategy;

import com.jdec.platform.config.api.dto.response.PermissionFieldInfo;
import com.jdec.platform.config.api.dto.response.PermissionTableGroupResp;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.api.engine.dto.request.DataQueryReq;
import com.jdec.platform.config.api.engine.dto.response.DataPageResp;
import com.jdec.platform.shared.context.AppContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectSelectStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

@Component("listDataEngineStrategy")
public class ListDataEngineStrategy extends AbstractDataEngineStrategy<DataQueryReq, DataPageResp> {

    // ThreadLocal 存储当前请求的可读字段列表
    private static final ThreadLocal<List<String>> READABLE_FIELDS = new ThreadLocal<>();

    @Override
    protected void checkPermission(
            String projectNo,
            Long subjectId,
            SysModuleCompleteResp moduleCompleteResp,
            DataQueryReq request) {
        Long userId = AppContext.getUserId();
        if (userId == null) {
            READABLE_FIELDS.set(null);
            return;
        }

        // 此处应通过userId查其roleId，为简化暂时假定为从其他服务获取到roleId或使用默认。这里暂时设定一个默认值，或从某个上下文中获取。
        Long roleId = 1L; // TODO: 真实环境中需要根据上下文处理角色获取

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

        // 2. 数据级权限校验 (行级)
        // TODO: 结合 SysDataPermissionApi 实现，暂时留空
    }

    @Override
    protected DataPageResp doExecute(
            SysModuleCompleteResp moduleCompleteResp, DataQueryReq request) {
        try {
            String projectNo = AppContext.getProjectNo();
            String primaryTable = moduleCompleteResp.getModule().getPrimaryTable();

            DSLContext dsl = dataSourceResolver.getDSLContext(projectNo);

            int offset = (request.getPageNum() - 1) * request.getPageSize();

            // 构建 jOOQ Query，过滤可读字段
            List<String> readableFields = READABLE_FIELDS.get();
            SelectSelectStep<Record> selectStep;

            if (readableFields != null && !readableFields.isEmpty()) {
                var jooqFields =
                        readableFields.stream().map(DSL::field).collect(Collectors.toList());
                selectStep = dsl.select(jooqFields);
            } else {
                selectStep = dsl.select();
            }

            var selectFromStep = selectStep.from(DSL.table(primaryTable));

            // 添加过滤条件
            if (request.getFilters() != null && !request.getFilters().isEmpty()) {
                for (Map.Entry<String, Object> entry : request.getFilters().entrySet()) {
                    selectFromStep.where(DSL.field(entry.getKey()).eq(entry.getValue()));
                }
            }

            // 执行分页查询
            Result<Record> fetch =
                    selectFromStep.limit(request.getPageSize()).offset(offset).fetch();

            // 查询总数
            var countStep = dsl.selectCount().from(DSL.table(primaryTable));
            if (request.getFilters() != null && !request.getFilters().isEmpty()) {
                for (Map.Entry<String, Object> entry : request.getFilters().entrySet()) {
                    countStep.where(DSL.field(entry.getKey()).eq(entry.getValue()));
                }
            }
            Long total = countStep.fetchOne(0, Long.class);

            List<Map<String, Object>> list = new ArrayList<>();
            for (Record record : fetch) {
                list.add(record.intoMap());
            }

            DataPageResp resp = new DataPageResp();
            resp.setPageNum(request.getPageNum());
            resp.setPageSize(request.getPageSize());
            resp.setTotal(total);
            resp.setList(list);

            return resp;
        } finally {
            READABLE_FIELDS.remove();
        }
    }
}
