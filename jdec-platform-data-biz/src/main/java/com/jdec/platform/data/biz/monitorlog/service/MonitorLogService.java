package com.jdec.platform.data.biz.monitorlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.data.api.MonitorLogApi;
import com.jdec.platform.data.api.constant.MonitorLogConstant;
import com.jdec.platform.data.api.dto.log.MonitorApproveBase;
import com.jdec.platform.data.api.dto.log.MonitorContentBase;
import com.jdec.platform.data.api.dto.log.MonitorLoginInfo;
import com.jdec.platform.data.api.dto.log.MonitorMockInfo;
import com.jdec.platform.data.api.dto.request.MonitorLogQueryReq;
import com.jdec.platform.data.api.dto.request.MonitorLogSaveReq;
import com.jdec.platform.data.api.dto.response.DataPage;
import com.jdec.platform.data.api.dto.response.MonitorLogResp;
import com.jdec.platform.data.biz.dsl.JooqDataContextFactory;
import com.jdec.platform.data.biz.monitorlog.entity.MonitorLog;
import com.jdec.platform.data.biz.monitorlog.handler.MonitorLogSaveHandler;
import com.jdec.platform.shared.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
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
    private final Map<Integer, MonitorLogSaveHandler> saveHandlers;

    public MonitorLogService(
            JooqDataContextFactory jooqDataContextFactory,
            ObjectMapper objectMapper,
            List<MonitorLogSaveHandler> handlers) {
        this.jooqDataContextFactory = jooqDataContextFactory;
        this.objectMapper = objectMapper;
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

        MonitorLog entity = handler.buildEntity(req);
        Map<Field<Object>, Object> values = toFieldValues(entity);
        jooqDataContextFactory
                .getContext()
                .insertInto(DSL.table(DSL.name(TABLE_MONITOR_LOG)))
                .set(values)
                .execute();
        log.debug(
                "保存监控日志：type={}, moduleId={}, dataId={}",
                entity.getType(),
                entity.getModuleId(),
                entity.getDataId());
    }

    // ==================== 保存校验 ====================

    /**
     * 校验日志类型与 approve / content 的子类型标识是否一致
     *
     * <p>子类独有字段靠子类型标识才能被反序列化：调用方传了独有字段却漏传标识时，Jackson 会按基类解析并**静默丢弃**这些字段， 落库后再也找不回来。故对象一旦提交（非
     * null），其子类型标识必须存在且与日志类型对应。
     *
     * <p>系统日志（type=2）只有共用字段，不做校验。
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
    public DataPage<MonitorLogResp> queryLogPage(MonitorLogQueryReq req) {
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
            return DataPage.empty(pageNo, pageSize);
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
        return DataPage.of(pageNo, pageSize, (long) total, records);
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
                                    .ge(req.getStartTime()));
        }
        if (req.getEndTime() != null) {
            condition =
                    condition.and(
                            DSL.field(DSL.name("record_time"), LocalDateTime.class)
                                    .le(req.getEndTime()));
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
