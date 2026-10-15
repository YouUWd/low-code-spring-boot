package com.jdec.platform.common.biz.monitorlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.common.api.MonitorLogApi;
import com.jdec.platform.common.api.dto.log.MonitorApproveBase;
import com.jdec.platform.common.api.dto.log.MonitorContentBase;
import com.jdec.platform.common.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.common.api.dto.log.MonitorMockInfo;
import com.jdec.platform.common.api.dto.request.MonitorLogQueryReq;
import com.jdec.platform.common.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.common.api.dto.request.MonitorOperateIndexSaveReq;
import com.jdec.platform.common.api.dto.response.MonitorLogResp;
import com.jdec.platform.common.api.dto.response.MonitorOperateIndexResp;
import com.jdec.platform.common.biz.monitorindex.service.MonitorOperateIndexService;
import com.jdec.platform.common.biz.monitorlog.entity.MonitorLog;
import com.jdec.platform.common.biz.monitorlog.handler.MonitorLogSaveHandler;
import com.jdec.platform.config.open.api.OpenMenuApi;
import com.jdec.platform.shared.constant.MonitorLogConstant;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.datasource.JooqDataContextFactory;
import com.jdec.platform.shared.exception.BusinessException;
import com.jdec.platform.shared.model.PageResult;
import com.jdec.platform.shared.security.SecurityUtils;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 监控日志 Service
 *
 * <p>表位于项目业务库，按当前上下文的项目编码（{@code X-Project-No} 请求头）动态解析数据源。
 *
 * <p>三种日志类型共用本表，类型独有字段以 JSON 字符串存放，转换在请求/响应 DTO 与本类中完成。
 */
@Slf4j
@Service
public class MonitorLogService implements MonitorLogApi {

    /** 监控日志表名（业务库） */
    private static final String TABLE_MONITOR_LOG = "monitor_log";

    /** 默认页码 */
    private static final int DEFAULT_PAGE_NO = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** LIKE 转义字符 */
    private static final char LIKE_ESCAPE = '\\';

    /** 日志表列（别名与行模型属性同名，保证记录精准映射到 {@link MonitorLog}） */
    private static final List<Field<?>> LOG_COLUMNS =
            List.of(
                    DSL.field(DSL.name("id"), Long.class).as("id"),
                    DSL.field(DSL.name("type"), Integer.class).as("type"),
                    DSL.field(DSL.name("sys_type"), String.class).as("sysType"),
                    DSL.field(DSL.name("subject_id"), Long.class).as("subjectId"),
                    DSL.field(DSL.name("subject_name"), String.class).as("subjectName"),
                    DSL.field(DSL.name("approve"), String.class).as("approve"),
                    DSL.field(DSL.name("login_info"), String.class).as("loginInfo"),
                    DSL.field(DSL.name("mock_info"), String.class).as("mockInfo"),
                    DSL.field(DSL.name("ip"), String.class).as("ip"),
                    DSL.field(DSL.name("module_id"), Long.class).as("moduleId"),
                    DSL.field(DSL.name("data_id"), Long.class).as("dataId"),
                    DSL.field(DSL.name("browser"), String.class).as("browser"),
                    DSL.field(DSL.name("plat_form"), String.class).as("platForm"),
                    DSL.field(DSL.name("content"), String.class).as("content"),
                    DSL.field(DSL.name("remark"), String.class).as("remark"),
                    DSL.field(DSL.name("record_time"), LocalDateTime.class).as("recordTime"),
                    DSL.field(DSL.name("created_date"), LocalDateTime.class).as("createdDate"));

    private final JooqDataContextFactory jooqDataContextFactory;
    private final ObjectMapper objectMapper;
    private final MonitorOperateIndexService monitorOperateIndexService;
    private final MonitorLogEnrichService monitorLogEnrichService;
    private final MonitorLogFieldPermissionService monitorLogFieldPermissionService;
    private final OpenMenuApi openMenuApi;
    private final Map<Integer, MonitorLogSaveHandler> saveHandlers;

    public MonitorLogService(
            JooqDataContextFactory jooqDataContextFactory,
            ObjectMapper objectMapper,
            MonitorOperateIndexService monitorOperateIndexService,
            MonitorLogEnrichService monitorLogEnrichService,
            MonitorLogFieldPermissionService monitorLogFieldPermissionService,
            OpenMenuApi openMenuApi,
            List<MonitorLogSaveHandler> handlers) {
        this.jooqDataContextFactory = jooqDataContextFactory;
        this.objectMapper = objectMapper;
        this.monitorOperateIndexService = monitorOperateIndexService;
        this.monitorLogEnrichService = monitorLogEnrichService;
        this.monitorLogFieldPermissionService = monitorLogFieldPermissionService;
        this.openMenuApi = openMenuApi;
        this.saveHandlers =
                handlers.stream()
                        .collect(
                                Collectors.toMap(
                                        MonitorLogSaveHandler::supportType,
                                        Function.identity(),
                                        (first, second) -> first));
    }

    @Override
    public void saveLog(MonitorLogSaveReq req) {
        saveLog(AppContext.getProjectNo(), req);
    }

    /**
     * 保存监控日志（指定项目编码解析数据源）。
     *
     * <p>用于日志表与业务库不同源的场景：调用方显式传入日志库对应的项目编码，无需依赖请求上下文。
     *
     * @param projectNo 数据源解析用的项目编码
     * @param req 监控日志保存请求
     */
    public void saveLog(String projectNo, MonitorLogSaveReq req) {
        if (req == null) {
            throw new BusinessException("监控日志不能为空");
        }
        if (req.getType() == null) {
            throw new BusinessException("日志类型不能为空");
        }
        MonitorLogSaveHandler handler = saveHandlers.get(req.getType());
        if (handler == null) {
            throw new BusinessException("不支持的日志类型：" + req.getType());
        }
        validateLogSubType(req);
        // 调用方只传 id，其余展示信息（用户、主体、角色）跨库查回后补全
        monitorLogEnrichService.enrich(projectNo, req);

        MonitorLog entity = handler.buildEntity(req);
        Map<Field<Object>, Object> values = toFieldValues(entity);

        // 操作日志与其索引必须同事务落库：索引写入失败时日志一并回滚
        jooqDataContextFactory
                .getContext(projectNo)
                .transaction(
                        configuration -> {
                            DSLContext tx = DSL.using(configuration);
                            tx.insertInto(DSL.table(DSL.name(TABLE_MONITOR_LOG)))
                                    .set(values)
                                    .execute();
                            saveOperateIndex(tx, entity);
                        });

        log.debug(
                "保存监控日志：projectNo={}, type={}, moduleId={}, dataId={}",
                projectNo,
                entity.getType(),
                entity.getModuleId(),
                entity.getDataId());
    }

    /**
     * 操作日志额外写入索引表 {@code monitor_operate_index}，便于按「模块 + 单据」反查该单据的操作日志。
     *
     * <p>使用调用方事务内的 {@code dsl} 写入，与日志落库共用同一连接。其他类型日志不建索引。
     */
    private void saveOperateIndex(DSLContext dsl, MonitorLog entity) {
        if (!MonitorLogConstant.TYPE_OPERATION_LOG.equals(entity.getType())) {
            return;
        }
        MonitorOperateIndexSaveReq indexReq = new MonitorOperateIndexSaveReq();
        indexReq.setLogId(currentInsertId(dsl));
        indexReq.setModuleId(entity.getModuleId());
        indexReq.setDataId(entity.getDataId());
        monitorOperateIndexService.saveIndex(dsl, indexReq);
    }

    /**
     * 取当前连接最近一次插入的自增主键。
     *
     * <p>MySQL 不支持 {@code INSERT .. RETURNING}，jOOQ 的 {@code returningResult()} 在该方言下会静默返回 {@code
     * null}，故改用同连接的 {@code LAST_INSERT_ID()}（jOOQ 的方言函数 {@link DSLContext#lastID()}）取主键。
     */
    private Long currentInsertId(DSLContext dsl) {
        BigInteger id = dsl.lastID();
        return id == null ? null : id.longValue();
    }

    // ==================== 保存校验 ====================

    /**
     * 校验日志类型与 approve / content 的子类型标识是否一致
     *
     * <p>子类独有字段靠子类型标识才能被反序列化：调用方传了独有字段却漏传标识时，Jackson 会按基类解析并**静默丢弃**这些字段， 落库后再也找不回来。故对象一旦提交（非
     * null），其子类型标识必须存在且与日志类型对应。
     *
     * <p>系统日志（type=2）的 content 只要提交，其子类型标识必须为 systemLog（不传 content 时不校验）。
     */
    private void validateLogSubType(MonitorLogSaveReq req) {
        if (MonitorLogConstant.TYPE_OPERATION_LOG.equals(req.getType())) {
            if (req.getApprove() != null) {
                checkLogSubType(
                        "approve",
                        req.getApprove().getLogSubType(),
                        MonitorLogConstant.OPERATION_LOG);
            }
            if (req.getContent() != null) {
                checkLogSubType(
                        "content",
                        req.getContent().getLogSubType(),
                        MonitorLogConstant.OPERATION_LOG);
            }
        } else if (MonitorLogConstant.TYPE_MESSAGE_LOG.equals(req.getType())) {
            if (req.getContent() != null) {
                checkLogSubType(
                        "content",
                        req.getContent().getLogSubType(),
                        MonitorLogConstant.MESSAGE_LOG);
            }
        } else if (MonitorLogConstant.TYPE_SYSTEM_LOG.equals(req.getType())) {
            if (req.getContent() != null) {
                checkLogSubType(
                        "content", req.getContent().getLogSubType(), MonitorLogConstant.SYSTEM_LOG);
            }
        }
    }

    /** 子类型标识校验：必须与日志类型对应的取值一致 */
    private void checkLogSubType(String field, String logSubType, String expected) {
        if (logSubType == null) {
            throw new BusinessException("监控日志 " + field + " 缺少子类型标识 logSubType，应为：" + expected);
        }
        if (!expected.equals(logSubType)) {
            throw new BusinessException(
                    "监控日志 "
                            + field
                            + " 的子类型标识 logSubType 与日志类型不匹配，应为："
                            + expected
                            + "，实际："
                            + logSubType);
        }
    }

    @Override
    public PageResult<MonitorLogResp> queryLogPage(MonitorLogQueryReq req) {
        int pageNo =
                req != null && req.getPageNo() != null && req.getPageNo() > 0
                        ? req.getPageNo()
                        : DEFAULT_PAGE_NO;
        int pageSize =
                req != null && req.getPageSize() != null && req.getPageSize() > 0
                        ? req.getPageSize()
                        : DEFAULT_PAGE_SIZE;

        DSLContext dsl = jooqDataContextFactory.getContext();
        Table<Record> table = DSL.table(DSL.name(TABLE_MONITOR_LOG));
        Condition condition = buildCondition(req);

        int total = dsl.fetchCount(table, condition);
        if (total == 0) {
            return PageResult.of((long) pageNo, (long) pageSize, 0L, List.of());
        }

        List<MonitorLogResp> records =
                dsl
                        .select(LOG_COLUMNS)
                        .from(table)
                        .where(condition)
                        .orderBy(
                                DSL.field(DSL.name("record_time")).desc(),
                                DSL.field(DSL.name("id")).desc())
                        .limit(pageSize)
                        .offset((pageNo - 1) * pageSize)
                        .fetchInto(MonitorLog.class)
                        .stream()
                        .map(this::toResp)
                        .collect(Collectors.toList());
        return PageResult.of((long) pageNo, (long) pageSize, (long) total, records);
    }

    /**
     * 按菜单 + 单据查询操作日志
     *
     * <p>两步查询：先由菜单ID 反查其关联的模块ID，再按 {@code module_id + data_id} 从索引表 {@code monitor_operate_index}
     * 取出日志 id，去 {@code monitor_log} 查明细（按记录时间倒序）。索引表只有操作日志（type=0）才会写入， 故无需再按类型过滤。
     *
     * <p>返回前按当前登录角色的字段权限过滤每条日志 {@code remark} 中的字段明细：非可查阅（view≠1）的字段从 {@code columns} 中剔除。
     */
    @Override
    public List<MonitorLogResp> queryOperateLog(Long menuId, Long dataId) {
        if (menuId == null || dataId == null) {
            throw new BusinessException("菜单id与单据id不能为空");
        }
        Long moduleId = openMenuApi.getModuleIdByMenuId(menuId);
        if (moduleId == null) {
            log.warn("菜单未关联模块，操作日志查询返回空: menuId={}", menuId);
            return List.of();
        }
        List<Long> logIds =
                monitorOperateIndexService.listByData(moduleId, dataId).stream()
                        .map(MonitorOperateIndexResp::getLogId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
        if (logIds.isEmpty()) {
            return List.of();
        }
        List<MonitorLogResp> records =
                jooqDataContextFactory
                        .getContext()
                        .select(LOG_COLUMNS)
                        .from(DSL.table(DSL.name(TABLE_MONITOR_LOG)))
                        .where(DSL.field(DSL.name("id"), Long.class).in(logIds))
                        .orderBy(DSL.field(DSL.name("record_time")), DSL.field(DSL.name("id")))
                        .fetchInto(MonitorLog.class)
                        .stream()
                        .map(this::toResp)
                        .collect(Collectors.toList());

        // 字段权限过滤：按当前角色 + 菜单子树模块的 view=1 字段，剔除 remark 中无权限的字段明细
        Set<String> readableFieldIndexes =
                monitorLogFieldPermissionService.readableFieldIndexes(
                        menuId, SecurityUtils.getRoleId());
        if (readableFieldIndexes != null) {
            records.forEach(
                    resp ->
                            resp.setRemark(
                                    monitorLogFieldPermissionService.filterRemark(
                                            resp.getRemark(), readableFieldIndexes)));
        }
        return records;
    }

    // ==================== 查询条件 ====================

    /** 组装查询条件：记录时间范围 + 日志类型 + 关键词模糊（content / remark） */
    private Condition buildCondition(MonitorLogQueryReq req) {
        Condition condition = DSL.trueCondition();
        if (req == null) {
            return condition;
        }
        if (req.getStartTime() != null) {
            condition =
                    condition.and(
                            DSL.field(DSL.name("record_time"), LocalDateTime.class)
                                    .ge(req.getStartTime().atStartOfDay()));
        }
        if (req.getEndTime() != null) {
            condition =
                    condition.and(
                            DSL.field(DSL.name("record_time"), LocalDateTime.class)
                                    .le(req.getEndTime().atTime(LocalTime.MAX)));
        }
        if (req.getType() != null) {
            condition = condition.and(DSL.field(DSL.name("type"), Integer.class).eq(req.getType()));
        }
        if (req.getKeyword() != null && !req.getKeyword().trim().isEmpty()) {
            String pattern = "%" + escapeLike(req.getKeyword().trim()) + "%";
            condition =
                    condition.and(
                            DSL.field(DSL.name("content"), String.class)
                                    .like(pattern, LIKE_ESCAPE)
                                    .or(
                                            DSL.field(DSL.name("remark"), String.class)
                                                    .like(pattern, LIKE_ESCAPE)));
        }
        return condition;
    }

    /** 转义关键词中的 LIKE 通配符，避免用户输入的 % _ 被当作通配符 */
    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ==================== 落库与回显 ====================

    /** 实体转列值（JSON 列以字符串写入，交由数据库解析） */
    private Map<Field<Object>, Object> toFieldValues(MonitorLog entity) {
        Map<Field<Object>, Object> values = new LinkedHashMap<>();
        values.put(DSL.field(DSL.name("type")), entity.getType());
        values.put(DSL.field(DSL.name("sys_type")), entity.getSysType());
        values.put(DSL.field(DSL.name("subject_id")), entity.getSubjectId());
        values.put(DSL.field(DSL.name("subject_name")), entity.getSubjectName());
        values.put(DSL.field(DSL.name("approve")), entity.getApprove());
        values.put(DSL.field(DSL.name("login_info")), entity.getLoginInfo());
        values.put(DSL.field(DSL.name("mock_info")), entity.getMockInfo());
        values.put(DSL.field(DSL.name("ip")), entity.getIp());
        values.put(DSL.field(DSL.name("module_id")), entity.getModuleId());
        values.put(DSL.field(DSL.name("data_id")), entity.getDataId());
        values.put(DSL.field(DSL.name("browser")), entity.getBrowser());
        values.put(DSL.field(DSL.name("plat_form")), entity.getPlatForm());
        values.put(DSL.field(DSL.name("content")), entity.getContent());
        values.put(DSL.field(DSL.name("remark")), entity.getRemark());
        values.put(DSL.field(DSL.name("record_time")), entity.getRecordTime());
        values.put(DSL.field(DSL.name("created_date")), entity.getCreatedDate());
        return values;
    }

    /** 行模型转响应：JSON 字符串反序列化为对象 */
    private MonitorLogResp toResp(MonitorLog entity) {
        return MonitorLogResp.builder()
                .id(entity.getId())
                .type(entity.getType())
                .sysType(entity.getSysType())
                .subjectId(entity.getSubjectId())
                .subjectName(entity.getSubjectName())
                .approve(parse(entity.getApprove(), MonitorApproveBase.class, "approve"))
                .loginInfo(parse(entity.getLoginInfo(), MonitorLoginInfo.class, "loginInfo"))
                .mockInfo(parse(entity.getMockInfo(), MonitorMockInfo.class, "mockInfo"))
                .ip(entity.getIp())
                .moduleId(entity.getModuleId())
                .dataId(entity.getDataId())
                .browser(entity.getBrowser())
                .platForm(entity.getPlatForm())
                .content(parse(entity.getContent(), MonitorContentBase.class, "content"))
                .remark(parse(entity.getRemark(), Object.class, "remark"))
                .recordTime(entity.getRecordTime())
                .createdDate(entity.getCreatedDate())
                .build();
    }

    /** JSON 字符串反序列化，为空或格式非法时返回 null（不影响整页日志返回） */
    private <T> T parse(String json, Class<T> type, String field) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("监控日志 {} 解析失败: json={}", field, json, e);
            return null;
        }
    }
}
