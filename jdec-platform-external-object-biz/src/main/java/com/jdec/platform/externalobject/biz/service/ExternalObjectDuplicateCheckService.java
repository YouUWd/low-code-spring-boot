package com.jdec.platform.externalobject.biz.service;

import com.jdec.platform.externalobject.api.ExternalObjectDuplicateCheckApi;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectDuplicateCheckReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectDuplicateCheckResp;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectDuplicateMatch;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectRealtimeRepository;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectTable;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectWordValues;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 外部对象字段查重实现。
 *
 * <p>流程：
 *
 * <ol>
 *   <li>解析 {@code table.field}，确认字段支持查重；
 *   <li>从 info 主表收集查重范围：active=1 的记录；若其 business_status 不是 99/-99（未终结），
 *       再补充同业务编号下最后一个启用版本（business_status=99 且 version_no 最大）的记录；
 *   <li>沿父表关系逐层下钻，收集字段所在表的记录ID；
 *   <li>在字段所在表内按字段值匹配。
 * </ol>
 *
 * <p>请求只包含 {@code table.field} 与字段值；人员姓名/手机号/证件号仅统计类型为 EMPLOYEE 的记录。
 */
@Service
@RequiredArgsConstructor
public class ExternalObjectDuplicateCheckService implements ExternalObjectDuplicateCheckApi {

    /** 单次查重最多返回的命中记录数 */
    private static final int MAX_MATCHES = 20;

    /** 支持查重的字段：table.field -> 查重定义 */
    private static final Map<String, DuplicateField> FIELDS = fields();

    private final ExternalObjectRealtimeRepository repository;

    @Override
    public ExternalObjectDuplicateCheckResp check(ExternalObjectDuplicateCheckReq req) {
        if (req == null) {
            throw new BusinessException("查重请求不能为空");
        }
        String field = ExternalObjectWordValues.trim(req.field()).toLowerCase(Locale.ROOT);
        if (field.isEmpty()) {
            throw new BusinessException("查重字段不能为空");
        }
        DuplicateField spec = FIELDS.get(field);
        if (spec == null) {
            throw new BusinessException("不支持查重的字段: " + req.field());
        }

        String value = ExternalObjectWordValues.toText(req.value());
        if (value.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }

        DSLContext dsl = repository.dsl();
        Set<Long> infoIds = collectInfoIds(dsl);
        if (infoIds.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }
        List<Long> leafIds = collectLeafIds(dsl, spec.table(), infoIds);
        if (leafIds.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }

        List<ExternalObjectDuplicateMatch> matches = queryMatches(dsl, spec, leafIds, value);
        if (matches.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }
        String companyName = matches.get(0).companyName();
        String message =
                String.format(
                        spec.messageTemplate(),
                        value,
                        StringUtils.isBlank(companyName) ? "其他外部对象" : companyName);
        return ExternalObjectDuplicateCheckResp.duplicated(message, matches);
    }

    private static Map<String, DuplicateField> fields() {
        Map<String, DuplicateField> map = new LinkedHashMap<>();
        map.put(
                "info.company_name",
                new DuplicateField(
                        ExternalObjectTable.INFO, "company_name", "%s已经存在，请重新填写", false));
        map.put(
                "basic_info.uscc",
                new DuplicateField(
                        ExternalObjectTable.BASIC_INFO,
                        "uscc",
                        "统一社会信用代码【%s】在外部对象【%s】中已经存在，请核对后重新填写！",
                        false));
        map.put(
                "basic_info.short_name",
                new DuplicateField(
                        ExternalObjectTable.BASIC_INFO,
                        "short_name",
                        "外部对象简称【%s】在外部对象【%s】中已经存在，请核对后重新填写！",
                        false));
        map.put(
                "basic_info.en_short_name",
                new DuplicateField(
                        ExternalObjectTable.BASIC_INFO,
                        "en_short_name",
                        "外部对象英文简称【%s】在外部对象【%s】中已经存在，请核对后重新填写！",
                        false));
        map.put(
                "basic_info.en_full_name",
                new DuplicateField(
                        ExternalObjectTable.BASIC_INFO,
                        "en_full_name",
                        "外部对象英文名全称【%s】在外部对象【%s】中已经存在，请核对后重新填写！",
                        false));
        map.put(
                "member.name",
                new DuplicateField(
                        ExternalObjectTable.MEMBER,
                        "name",
                        "姓名【%s】的人员信息在外部对象【%s】中已存在，是否继续添加？",
                        true));
        map.put(
                "member.mobile_phone_no",
                new DuplicateField(
                        ExternalObjectTable.MEMBER,
                        "mobile_phone_no",
                        "手机号【%s】在外部对象【%s】中已存在，请核对后重新填写！",
                        true));
        map.put(
                "member.identity_no",
                new DuplicateField(
                        ExternalObjectTable.MEMBER,
                        "identity_no",
                        "证件号【%s】在外部对象【%s】中已存在，请核对后重新填写！",
                        true));
        return Collections.unmodifiableMap(map);
    }

    /**
     * 收集 info 主表需要参与查重的主键ID。
     *
     * <p>规则：active=1 的记录；若其 business_status 不是 99/-99（尚未生效或已作废），
     * 再补充同业务编号下最后一个启用版本（business_status=99 且 version_no 最大）的记录。
     */
    private Set<Long> collectInfoIds(DSLContext dsl) {
        Condition activeCondition =
                ExternalObjectTable.INFO
                        .intField("active")
                        .eq(1)
                        .and(ExternalObjectTable.INFO.intField("deleted").eq(0));

        var activeRecords =
                dsl.select(
                                ExternalObjectTable.INFO.longField("id"),
                                ExternalObjectTable.INFO.textField("business_no"),
                                ExternalObjectTable.INFO.intField("business_status"))
                        .from(ExternalObjectTable.INFO.table())
                        .where(activeCondition)
                        .fetch();

        Set<Long> infoIds = new LinkedHashSet<>();
        Set<String> unfinishedBusinessNos = new LinkedHashSet<>();
        for (Record record : activeRecords) {
            infoIds.add(record.get(ExternalObjectTable.INFO.longField("id")));
            String businessNo = record.get(ExternalObjectTable.INFO.textField("business_no"));
            Integer businessStatus =
                    record.get(ExternalObjectTable.INFO.intField("business_status"));
            boolean terminal =
                    businessStatus != null && (businessStatus == 99 || businessStatus == -99);
            if (!terminal && StringUtils.isNotBlank(businessNo)) {
                unfinishedBusinessNos.add(businessNo);
            }
        }
        if (unfinishedBusinessNos.isEmpty()) {
            return infoIds;
        }

        Condition enabledCondition =
                ExternalObjectTable.INFO
                        .intField("business_status")
                        .eq(99)
                        .and(ExternalObjectTable.INFO.intField("deleted").eq(0))
                        .and(
                                ExternalObjectTable.INFO
                                        .textField("business_no")
                                        .in(unfinishedBusinessNos));
        Table<?> latestVersionTable =
                dsl.select(
                                ExternalObjectTable.INFO.textField("business_no").as("b_no"),
                                DSL.max(ExternalObjectTable.INFO.intField("version_no"))
                                        .as("max_v"))
                        .from(ExternalObjectTable.INFO.table())
                        .where(enabledCondition)
                        .groupBy(ExternalObjectTable.INFO.textField("business_no"))
                        .asTable("latest_version");

        infoIds.addAll(
                dsl.select(ExternalObjectTable.INFO.longField("id"))
                        .from(ExternalObjectTable.INFO.table())
                        .join(latestVersionTable)
                        .on(
                                ExternalObjectTable.INFO
                                        .textField("business_no")
                                        .eq(
                                                DSL.field(
                                                        DSL.name("latest_version", "b_no"),
                                                        String.class))
                                        .and(
                                                ExternalObjectTable.INFO
                                                        .intField("version_no")
                                                        .eq(
                                                                DSL.field(
                                                                        DSL.name(
                                                                                "latest_version",
                                                                                "max_v"),
                                                                        Integer.class))))
                        .where(enabledCondition)
                        .fetch(ExternalObjectTable.INFO.longField("id")));
        return infoIds;
    }

    /** 从 info 逐层下钻，收集字段所在表的记录ID。 */
    private List<Long> collectLeafIds(
            DSLContext dsl, ExternalObjectTable leafTable, Set<Long> infoIds) {
        List<Long> currentIds = new ArrayList<>(infoIds);
        for (ExternalObjectTable child : leafTable.pathFromInfo()) {
            if (currentIds.isEmpty()) {
                return List.of();
            }
            currentIds =
                    dsl.select(child.longField("id"))
                            .from(child.table())
                            .where(child.longField(child.parentIdColumn()).in(currentIds))
                            .and(child.intField("deleted").eq(0))
                            .fetch(child.longField("id"));
        }
        return currentIds;
    }

    /**
     * 在字段所在表内按值匹配。
     *
     * <p>表由 {@link DSL#table} 动态构建，结果集字段类型取自 JDBC 元数据：MySQL 的 BIGINT UNSIGNED 会映射为 jOOQ 的无符号类型，而
     * {@code record.get(Field)} 不做类型转换，按 {@code Long} 直接强转会抛 ClassCastException，因此主键统一通过 {@code
     * record.get(String, Class)} 转换取值。
     */
    private List<ExternalObjectDuplicateMatch> queryMatches(
            DSLContext dsl, DuplicateField spec, List<Long> leafIds, String value) {
        Condition condition =
                DSL.trim(spec.table().textField(spec.column()))
                        .eq(value)
                        .and(spec.table().longField("id").in(leafIds))
                        .and(spec.table().intField("deleted").eq(0));
        if (spec.requireEmployee()) {
            condition = condition.and(employeeTypeCondition());
        }

        if (spec.table() == ExternalObjectTable.INFO) {
            return dsl.selectFrom(ExternalObjectTable.INFO.table())
                    .where(condition)
                    .limit(MAX_MATCHES)
                    .fetch()
                    .map(
                            record ->
                                    new ExternalObjectDuplicateMatch(
                                            record.get("id", Long.class),
                                            record.get(
                                                    ExternalObjectTable.INFO.textField(
                                                            "company_name")),
                                            record.intoMap()));
        }

        var records =
                dsl.select(
                                spec.table().table().asterisk(),
                                ExternalObjectTable.INFO
                                        .textField("company_name")
                                        .as("info_company_name"))
                        .from(spec.table().table())
                        .join(ExternalObjectTable.INFO.table())
                        .on(
                                spec.table()
                                        .longField("company_id")
                                        .eq(ExternalObjectTable.INFO.longField("id")))
                        .where(condition)
                        .limit(MAX_MATCHES)
                        .fetch();
        List<ExternalObjectDuplicateMatch> matches = new ArrayList<>(records.size());
        for (Record record : records) {
            Map<String, Object> recordMap = new LinkedHashMap<>(record.intoMap());
            String companyName = (String) recordMap.remove("info_company_name");
            matches.add(
                    new ExternalObjectDuplicateMatch(
                            record.get("company_id", Long.class), companyName, recordMap));
        }
        return matches;
    }

    /** 人员类型为 EMPLOYEE（兼容小写与中文）的记录条件。 */
    private static Condition employeeTypeCondition() {
        return DSL.lower(ExternalObjectTable.MEMBER.textField("type"))
                .eq("employee")
                .or(ExternalObjectTable.MEMBER.textField("type").eq("员工"));
    }

    /**
     * 查重字段定义。
     *
     * @param table 字段所在表
     * @param column 字段列名
     * @param messageTemplate 重复提示模板：第一个占位符为字段值，第二个为已存在的外部对象全称
     * @param requireEmployee 仅统计人员类型为员工的记录
     */
    private record DuplicateField(
            ExternalObjectTable table,
            String column,
            String messageTemplate,
            boolean requireEmployee) {}
}
