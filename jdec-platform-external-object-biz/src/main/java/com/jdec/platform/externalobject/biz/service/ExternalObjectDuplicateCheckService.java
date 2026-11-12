package com.jdec.platform.externalobject.biz.service;

import com.jdec.platform.externalobject.api.ExternalObjectDuplicateCheckApi;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectDuplicateCheckReq;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectDuplicateCheckResp;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectDuplicateMatch;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectMemberDuplicateRule;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectRealtimeRepository;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectRealtimeRepository.MemberCandidate;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectRealtimeRepository.MemberRow;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectTable;
import com.jdec.platform.externalobject.biz.service.support.ExternalObjectWordValues;
import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 外部对象字段查重实现。
 *
 * <p>流程：
 *
 * <ol>
 *   <li>解析 {@code table.field}，确认字段支持查重；
 *   <li>收集参与查重的主单ID：正在走流程（{@code active=1} 且 business_status 非 99/-99，业务状态为空亦视为在流程中）
 *       与最后启用（business_status=99 且 active=1，即当前生效的启用记录）；business_status=-99 与 business_status=99 但
 *       active=0（已不再生效的上一启用版本）的记录不参与查重；
 *   <li>用收集到的主单ID沿父表关系逐层下钻，收集字段所在表的记录ID；
 *   <li>取出这些记录后比对字段值，比较口径与保存/提交校验的查重一致（去空白，统一社会信用代码另转大写）。
 * </ol>
 *
 * <p>请求只包含 {@code table.field} 与字段值。人员字段（姓名、手机号、证件号）不是单字段查重，而是按三者组合判断： 其余两个字段的值与自身记录由 {@code subId}
 * 查询人员行补齐；比对范围包含同一主单的其它人员行与其它外部对象，排除当前人员行本身； 非人员字段不排除当前对象自身（前端可按 {@code companyId}
 * 过滤），但同样排除当前对象的历史版本。
 *
 * <p>查重请求不合法时统一返回 {@link ApiCodeEnum#WARNING_EXISTS}（702），与其它业务异常的 700 区分。
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
            throw new BusinessException(ApiCodeEnum.WARNING_EXISTS.getCode(), "查重请求不能为空");
        }
        String field = ExternalObjectWordValues.trim(req.field()).toLowerCase(Locale.ROOT);
        if (field.isEmpty()) {
            throw new BusinessException(ApiCodeEnum.WARNING_EXISTS.getCode(), "查重字段不能为空");
        }
        String value = ExternalObjectWordValues.toText(req.value());
        if (value.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }
        if (ExternalObjectMemberDuplicateRule.FIELDS.contains(field)) {
            return checkMember(req, field, value);
        }
        DuplicateField spec = FIELDS.get(field);
        if (spec == null) {
            throw new BusinessException(
                    ApiCodeEnum.WARNING_EXISTS.getCode(), "不支持查重的字段: " + req.field());
        }
        // 比较口径与保存/提交校验一致：去空白，统一社会信用代码另转大写
        String normalizedValue = spec.normalize().apply(value);
        if (normalizedValue.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }

        DSLContext dsl = repository.dsl();
        Set<Long> infoIds = collectInfoIds(dsl);
        excludeHistoryVersions(infoIds, resolveInfoId(req, spec.table()));
        if (infoIds.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }
        List<Long> leafIds = collectLeafIds(dsl, spec.table(), infoIds);
        if (leafIds.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }

        List<ExternalObjectDuplicateMatch> matches =
                queryMatches(dsl, spec, leafIds, normalizedValue);
        if (matches.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }
        String companyName = matches.get(0).companyName();
        String message =
                String.format(
                        spec.messageTemplate(),
                        normalizedValue,
                        StringUtils.isBlank(companyName) ? "其他外部对象" : companyName);
        return ExternalObjectDuplicateCheckResp.duplicated(message, matches);
    }

    /**
     * 人员组合查重：姓名、手机号、证件号三者组合判断。
     *
     * <p>当前编辑字段取请求值，其余两个字段按 {@code subId} 读取已保存的人员行补齐；比对范围包含同一主单的其它人员行与其它外部对象，
     * 排除当前人员行本身与当前对象的历史版本。部门行不参与人员查重。
     */
    private ExternalObjectDuplicateCheckResp checkMember(
            ExternalObjectDuplicateCheckReq req, String field, String value) {
        MemberRow current = repository.findMember(req.subId());
        Long infoId = resolveInfoId(req, ExternalObjectTable.MEMBER);
        String nature = repository.findInfoNature(infoId);
        boolean individual = ExternalObjectWordValues.isIndividualNature(nature);
        if (!individual
                && ExternalObjectWordValues.isDepartmentType(
                        current == null ? null : current.type())) {
            return ExternalObjectDuplicateCheckResp.pass();
        }

        String name =
                ExternalObjectMemberDuplicateRule.normalizeName(
                        memberValue(
                                field,
                                ExternalObjectMemberDuplicateRule.NAME_FIELD,
                                value,
                                current));
        String mobilePhoneNo =
                ExternalObjectMemberDuplicateRule.normalizeMobilePhoneNo(
                        memberValue(
                                field,
                                ExternalObjectMemberDuplicateRule.MOBILE_FIELD,
                                value,
                                current));
        String identityNo =
                ExternalObjectMemberDuplicateRule.normalizeIdentityNo(
                        memberValue(
                                field,
                                ExternalObjectMemberDuplicateRule.IDENTITY_FIELD,
                                value,
                                current));
        if (name.isEmpty() && mobilePhoneNo.isEmpty() && identityNo.isEmpty()) {
            return ExternalObjectDuplicateCheckResp.pass();
        }

        DSLContext dsl = repository.dsl();
        Set<Long> infoIds = collectInfoIds(dsl);
        excludeHistoryVersions(infoIds, infoId);
        // 同一主单的其它人员行也要参与比对；当前主单不在查重范围时（如草稿）单独补上
        if (infoId != null) {
            infoIds.add(infoId);
        }
        List<Long> memberIds = collectLeafIds(dsl, ExternalObjectTable.MEMBER, infoIds);
        List<MemberCandidate> candidates =
                repository.findMemberCandidates(memberIds, name, mobilePhoneNo, identityNo);

        String message = null;
        List<ExternalObjectDuplicateMatch> matches = new ArrayList<>();
        for (MemberCandidate candidate : candidates) {
            if (req.subId() != null && req.subId().equals(candidate.id())) {
                continue;
            }
            // 与保存校验一致：对象性质非个人时，部门行不参与人员比对
            if (!individual && ExternalObjectWordValues.isDepartmentType(candidate.type())) {
                continue;
            }
            String companyName =
                    ExternalObjectMemberDuplicateRule.objectName(
                            candidate.companyName(),
                            candidate.companyId() != null && candidate.companyId().equals(infoId)
                                    ? "当前外部对象"
                                    : "其他外部对象");
            String candidateMessage =
                    ExternalObjectMemberDuplicateRule.message(
                            field,
                            name,
                            mobilePhoneNo,
                            identityNo,
                            candidateName(candidate),
                            ExternalObjectMemberDuplicateRule.normalizeMobilePhoneNo(
                                    candidate.mobilePhoneNo()),
                            ExternalObjectMemberDuplicateRule.normalizeIdentityNo(
                                    candidate.identityNo()),
                            companyName);
            if (candidateMessage == null) {
                continue;
            }
            message = message == null ? candidateMessage : message;
            matches.add(
                    new ExternalObjectDuplicateMatch(
                            candidate.companyId(), companyName, memberRecord(candidate)));
            if (matches.size() >= MAX_MATCHES) {
                break;
            }
        }
        return message == null
                ? ExternalObjectDuplicateCheckResp.pass()
                : ExternalObjectDuplicateCheckResp.duplicated(message, matches);
    }

    /** 解析当前记录所属的外部对象主单ID：{@code companyId} 为空时按 {@code subId} 反查。 */
    private Long resolveInfoId(ExternalObjectDuplicateCheckReq req, ExternalObjectTable table) {
        return repository.resolveInfoId(table, req.companyId(), req.subId());
    }

    /**
     * 排除当前对象的历史版本（同一业务编号下的其它版本）。
     *
     * <p>历史版本与当前对象的字段值基本一致，参与比对只会提示“与自己重复”；当前对象本身仍保留在查重范围内。
     */
    private void excludeHistoryVersions(Set<Long> infoIds, Long infoId) {
        if (infoId == null) {
            return;
        }
        String businessNo = repository.findBusinessNo(infoId);
        if (StringUtils.isBlank(businessNo)) {
            return;
        }
        Set<Long> sameBusinessIds =
                new LinkedHashSet<>(repository.findInfoIdsByBusinessNo(businessNo));
        sameBusinessIds.remove(infoId);
        infoIds.removeAll(sameBusinessIds);
    }

    /** 读取当前人员字段值：当前编辑字段取请求值，其余从人员行补齐。 */
    private static String memberValue(
            String field, String targetField, String value, MemberRow current) {
        if (targetField.equals(field)) {
            return value;
        }
        if (current == null) {
            return "";
        }
        return switch (targetField) {
            case ExternalObjectMemberDuplicateRule.NAME_FIELD -> current.name();
            case ExternalObjectMemberDuplicateRule.MOBILE_FIELD -> current.mobilePhoneNo();
            case ExternalObjectMemberDuplicateRule.IDENTITY_FIELD -> current.identityNo();
            default -> "";
        };
    }

    /** 比对人员姓名：优先 {@code name}，为空时取 {@code member_name}（与保存校验一致）。 */
    private static String candidateName(MemberCandidate candidate) {
        String name = ExternalObjectMemberDuplicateRule.normalizeName(candidate.name());
        return name.isEmpty()
                ? ExternalObjectMemberDuplicateRule.normalizeName(candidate.memberName())
                : name;
    }

    /** 命中人员记录，供前端展示已存在的人员信息。 */
    private static Map<String, Object> memberRecord(MemberCandidate candidate) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", candidate.id());
        record.put("name", candidateName(candidate));
        record.put("mobile_phone_no", candidate.mobilePhoneNo());
        record.put("identity_no", candidate.identityNo());
        return record;
    }

    private static Map<String, DuplicateField> fields() {
        Map<String, DuplicateField> map = new LinkedHashMap<>();
        map.put(
                "info.company_name",
                new DuplicateField(
                        ExternalObjectTable.INFO,
                        "company_name",
                        "%s已经存在，请重新填写",
                        ExternalObjectWordValues::trim));
        map.put(
                "basic_info.uscc",
                new DuplicateField(
                        ExternalObjectTable.BASIC_INFO,
                        "uscc",
                        "该[统一社会信用代码]已存在！",
                        ExternalObjectWordValues::compactUpper));
        map.put(
                "basic_info.short_name",
                new DuplicateField(
                        ExternalObjectTable.BASIC_INFO,
                        "short_name",
                        "该[外部对象简称]已存在！",
                        ExternalObjectWordValues::trim));
        map.put(
                "basic_info.en_short_name",
                new DuplicateField(
                        ExternalObjectTable.BASIC_INFO,
                        "en_short_name",
                        "该[外部对象英文简称]已存在！",
                        ExternalObjectWordValues::trim));
        map.put(
                "basic_info.en_full_name",
                new DuplicateField(
                        ExternalObjectTable.BASIC_INFO,
                        "en_full_name",
                        "该[外部对象英文名全称]已存在！",
                        ExternalObjectWordValues::trim));
        return Collections.unmodifiableMap(map);
    }

    /**
     * 收集顶层父模块（info）参与查重的主键ID。
     *
     * <p>查重范围（均为未删除）：
     *
     * <ul>
     *   <li>正在走流程：{@code active=1} 且 business_status 不是 99/-99（业务状态为空的新建/草稿记录亦视为在流程中）；
     *   <li>最后启用：{@code active=1} 且 business_status=99（当前生效的启用记录）。
     * </ul>
     *
     * <p>business_status=-99（已终结/作废）与 business_status=99 但 {@code active=0}（已不再生效的上一启用版本）不参与查重。
     * 当前外部对象自身及其同业务编号的历史版本由 {@link #excludeHistoryVersions} 排除。
     */
    private Set<Long> collectInfoIds(DSLContext dsl) {
        Condition scope = inProcessInfoCondition().or(lastEnabledInfoCondition());
        List<Long> ids =
                dsl.select(ExternalObjectTable.INFO.longField("id"))
                        .from(ExternalObjectTable.INFO.table())
                        .where(scope)
                        .and(ExternalObjectTable.INFO.intField("deleted").eq(0))
                        .fetch(ExternalObjectTable.INFO.longField("id"), Long.class);
        return new LinkedHashSet<>(ids);
    }

    /** 正在走流程：active=1 且 business_status 不是 99/-99（business_status 为空也视为在流程中）。 */
    private static Condition inProcessInfoCondition() {
        return ExternalObjectTable.INFO
                .intField("active")
                .eq(1)
                .and(
                        ExternalObjectTable.INFO
                                .intField("business_status")
                                .isNull()
                                .or(
                                        ExternalObjectTable.INFO
                                                .intField("business_status")
                                                .notIn(99, -99)));
    }

    /** 最后启用：active=1 且 business_status=99（当前生效的启用记录）。 */
    private static Condition lastEnabledInfoCondition() {
        return ExternalObjectTable.INFO
                .intField("business_status")
                .eq(99)
                .and(ExternalObjectTable.INFO.intField("active").eq(1));
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
     * 在字段所在表内按字段值比对。
     *
     * <p>表由 {@link DSL#table} 动态构建，结果集字段类型取自 JDBC 元数据：MySQL 的 BIGINT UNSIGNED 会映射为 jOOQ 的无符号类型，而
     * {@code record.get(Field)} 不做类型转换，按 {@code Long} 直接强转会抛 ClassCastException，因此主键统一通过 {@code
     * record.get(String, Class)} 转换取值。
     *
     * <p>先把候选记录取回、再在内存中按规范化后的值比对（与保存/提交校验的查重口径一致），而不是在 SQL 里直接等值比较， 避免已保存值两端带空白、统一社会信用代码大小写不一致时漏判。
     */
    private List<ExternalObjectDuplicateMatch> queryMatches(
            DSLContext dsl, DuplicateField spec, List<Long> leafIds, String value) {
        Condition condition =
                spec.table()
                        .longField("id")
                        .in(leafIds)
                        .and(spec.table().intField("deleted").eq(0));

        List<ExternalObjectDuplicateMatch> matches = new ArrayList<>();
        if (spec.table() == ExternalObjectTable.INFO) {
            for (Record record :
                    dsl.selectFrom(ExternalObjectTable.INFO.table()).where(condition).fetch()) {
                String candidate = record.get(ExternalObjectTable.INFO.textField("company_name"));
                if (!sameValue(spec, candidate, value)) {
                    continue;
                }
                matches.add(
                        new ExternalObjectDuplicateMatch(
                                record.get("id", Long.class), candidate, record.intoMap()));
                if (matches.size() >= MAX_MATCHES) {
                    break;
                }
            }
            return matches;
        }

        var records =
                dsl.select(
                                spec.table().table().asterisk(),
                                spec.table().textField(spec.column()).as("dup_value"),
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
                        .fetch();
        for (Record record : records) {
            if (!sameValue(spec, record.get("dup_value", String.class), value)) {
                continue;
            }
            Map<String, Object> recordMap = new LinkedHashMap<>(record.intoMap());
            recordMap.remove("dup_value");
            String companyName = (String) recordMap.remove("info_company_name");
            matches.add(
                    new ExternalObjectDuplicateMatch(
                            record.get("company_id", Long.class), companyName, recordMap));
            if (matches.size() >= MAX_MATCHES) {
                break;
            }
        }
        return matches;
    }

    /** 已保存值是否与请求值重复：两侧都按字段口径规范化后比较。 */
    private static boolean sameValue(DuplicateField spec, String candidate, String value) {
        return !value.isEmpty() && value.equals(spec.normalize().apply(candidate));
    }

    /**
     * 查重字段定义。
     *
     * @param table 字段所在表
     * @param column 字段列名
     * @param messageTemplate 重复提示模板：第一个占位符为字段值，第二个为已存在的外部对象全称
     * @param normalize 字段值规范化：请求值与已保存值都按此口径比较（与保存/提交校验一致）
     */
    private record DuplicateField(
            ExternalObjectTable table,
            String column,
            String messageTemplate,
            UnaryOperator<String> normalize) {}
}
