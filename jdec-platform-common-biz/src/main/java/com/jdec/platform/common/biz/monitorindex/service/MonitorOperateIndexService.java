package com.jdec.platform.common.biz.monitorindex.service;

import com.jdec.platform.common.api.MonitorOperateIndexApi;
import com.jdec.platform.common.api.dto.request.MonitorOperateIndexSaveReq;
import com.jdec.platform.common.api.dto.response.MonitorOperateIndexResp;
import com.jdec.platform.common.biz.monitorindex.entity.MonitorOperateIndex;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 操作日志索引 Service
 *
 * <p>操作日志的索引表，一条操作日志对应一条索引记录，用于按「模块 + 单据」反查该单据的操作日志。
 *
 * <p>表位于项目业务库，按当前上下文的项目编码（{@code X-Project-No} 请求头）动态解析数据源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorOperateIndexService implements MonitorOperateIndexApi {

    /** 操作日志索引表名（业务库） */
    private static final String TABLE_MONITOR_OPERATE_INDEX = "monitor_operate_index";

    /** 索引表列（别名与行模型属性同名，保证记录精准映射到 {@link MonitorOperateIndex}） */
    private static final List<Field<?>> INDEX_COLUMNS =
            List.of(
                    DSL.field(DSL.name("id"), Long.class).as("id"),
                    DSL.field(DSL.name("log_id"), Long.class).as("logId"),
                    DSL.field(DSL.name("module_id"), Long.class).as("moduleId"),
                    DSL.field(DSL.name("data_id"), Long.class).as("dataId"));

    private final JooqDataContextFactory jooqDataContextFactory;

    @Override
    public void saveIndex(MonitorOperateIndexSaveReq req) {
        saveIndex(jooqDataContextFactory.getContext(AppContext.getProjectNo()), req);
    }

    /**
     * 保存操作日志索引（使用指定的 DSLContext）。
     *
     * <p>供调用方在自己的事务内写入，与业务写入共用同一连接：传入 {@code dsl.transaction(...)} 内由 {@code
     * DSL.using(configuration)} 构建的上下文即可保证同事务。
     *
     * @param dsl DSLContext（可为事务内上下文）
     * @param req 操作日志索引保存请求
     */
    public void saveIndex(DSLContext dsl, MonitorOperateIndexSaveReq req) {
        Map<Field<Object>, Object> values = new LinkedHashMap<>();
        values.put(DSL.field(DSL.name("log_id")), req.getLogId());
        values.put(DSL.field(DSL.name("module_id")), req.getModuleId());
        values.put(DSL.field(DSL.name("data_id")), req.getDataId());
        dsl.insertInto(DSL.table(DSL.name(TABLE_MONITOR_OPERATE_INDEX))).set(values).execute();
        log.debug(
                "保存操作日志索引：logId={}, moduleId={}, dataId={}",
                req.getLogId(),
                req.getModuleId(),
                req.getDataId());
    }

    /**
     * 按模块 + 单据查询操作日志索引
     *
     * <p>表无 project_no 列，不做数据隔离过滤；按主键升序返回，结果顺序与日志写入顺序一致。
     */
    @Override
    public List<MonitorOperateIndexResp> listByData(Long moduleId, Long dataId) {
        if (moduleId == null || dataId == null) {
            throw new BusinessException("模块id与单据id不能为空");
        }
        return jooqDataContextFactory
                .getContext()
                .select(INDEX_COLUMNS)
                .from(DSL.table(DSL.name(TABLE_MONITOR_OPERATE_INDEX)))
                .where(
                        DSL.field(DSL.name("module_id"), Long.class)
                                .eq(moduleId)
                                .and(DSL.field(DSL.name("data_id"), Long.class).eq(dataId)))
                .orderBy(DSL.field(DSL.name("id")).asc())
                .fetchInto(MonitorOperateIndex.class)
                .stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    /** 行模型转响应 */
    private MonitorOperateIndexResp toResp(MonitorOperateIndex entity) {
        return MonitorOperateIndexResp.builder()
                .id(entity.getId())
                .logId(entity.getLogId())
                .moduleId(entity.getModuleId())
                .dataId(entity.getDataId())
                .build();
    }
}
