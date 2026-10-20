package com.jdec.platform.externalobject.biz.service;

import com.jdec.platform.externalobject.api.ExternalObjectQueryApi;
import com.jdec.platform.externalobject.api.dto.ExternalObjectQueryField;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectFieldQuery;
import com.jdec.platform.externalobject.api.dto.request.ExternalObjectMemberQuery;
import com.jdec.platform.externalobject.api.dto.response.ExternalObjectMemberMatch;
import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

/**
 * 外部对象数据查询实现。
 *
 * <p>直连 {@code company} 业务库，供业务校验做已保存数据查重。查询只统计当前有效、未删除且未排除的外部对象：
 *
 * <ul>
 *   <li>{@code info.active} 为 1 或为空、{@code info.deleted = 0}；
 *   <li>{@code excludeBusinessNo} 对应的外部对象（含其历史版本）不参与查重。
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExternalObjectQueryService implements ExternalObjectQueryApi {

    private static final Table<?> INFO = DSL.table(DSL.name("info"));

    private static final Table<?> INFO_ALIAS = DSL.table(DSL.name("info")).as("i");

    private static final Table<?> BASIC_INFO_ALIAS = DSL.table(DSL.name("basic_info")).as("b");

    private static final Table<?> MEMBER_ALIAS = DSL.table(DSL.name("member")).as("m");

    private final DataSourceResolver dataSourceResolver;

    @Override
    public Set<String> findDuplicateValues(ExternalObjectFieldQuery query) {
        if (query == null || query.field() == null || query.values().isEmpty()) {
            return Set.of();
        }
        List<String> values = query.values().stream().filter(StringUtils::isNotBlank).toList();
        if (values.isEmpty()) {
            return Set.of();
        }

        DSLContext dsl = dsl();
        String column = columnOf(query.field());
        Condition scope =
                scopeCondition(
                        dsl,
                        query.subjectId(),
                        query.excludeCompanyId(),
                        query.excludeBusinessNo());

        List<String> found;
        if (query.field() == ExternalObjectQueryField.COMPANY_NAME) {
            found =
                    dsl.selectDistinct(field("i", column).as("value"))
                            .from(INFO_ALIAS)
                            .where(field("i", column).in(values))
                            .and(infoActiveCondition())
                            .and(scope)
                            .fetch("value", String.class);
        } else {
            found =
                    dsl.selectDistinct(field("b", column).as("value"))
                            .from(BASIC_INFO_ALIAS)
                            .join(INFO_ALIAS)
                            .on(longField("b", "company_id").eq(longField("i", "id")))
                            .where(field("b", column).in(values))
                            .and(intField("b", "deleted").eq(0))
                            .and(infoActiveCondition())
                            .and(scope)
                            .fetch("value", String.class);
        }
        Set<String> result = new HashSet<>();
        for (String value : found) {
            if (StringUtils.isNotBlank(value)) {
                result.add(value);
            }
        }
        return result;
    }

    @Override
    public List<ExternalObjectMemberMatch> findDuplicateMembers(ExternalObjectMemberQuery query) {
        if (query == null || (query.mobilePhoneNos().isEmpty() && query.identityNos().isEmpty())) {
            return List.of();
        }
        List<String> mobiles = nonBlank(query.mobilePhoneNos());
        List<String> identities = nonBlank(query.identityNos());
        if (mobiles.isEmpty() && identities.isEmpty()) {
            return List.of();
        }

        DSLContext dsl = dsl();
        List<Condition> conditions = new ArrayList<>();
        conditions.add(intField("m", "deleted").eq(0));
        conditions.add(infoActiveCondition());
        conditions.add(
                scopeCondition(
                        dsl,
                        query.subjectId(),
                        query.excludeCompanyId(),
                        query.excludeBusinessNo()));

        Condition numberMatch = null;
        if (!mobiles.isEmpty()) {
            numberMatch = field("m", "mobile_phone_no").in(mobiles);
        }
        if (!identities.isEmpty()) {
            Condition identityMatch = field("m", "identity_no").in(identities);
            numberMatch = numberMatch == null ? identityMatch : numberMatch.or(identityMatch);
        }
        conditions.add(numberMatch);

        var records =
                dsl.select(
                                field("m", "mobile_phone_no").as("mobilePhoneNo"),
                                field("m", "identity_no").as("identityNo"),
                                field("m", "name").as("memberName"),
                                field("m", "member_name").as("memberNickName"),
                                field("i", "company_name").as("companyName"))
                        .from(MEMBER_ALIAS)
                        .join(INFO_ALIAS)
                        .on(longField("m", "company_id").eq(longField("i", "id")))
                        .where(conditions.toArray(new Condition[0]))
                        .fetch();

        List<ExternalObjectMemberMatch> result = new ArrayList<>(records.size());
        for (Record record : records) {
            String memberName = record.get("memberName", String.class);
            if (StringUtils.isBlank(memberName)) {
                memberName = record.get("memberNickName", String.class);
            }
            result.add(
                    new ExternalObjectMemberMatch(
                            record.get("mobilePhoneNo", String.class),
                            record.get("identityNo", String.class),
                            memberName,
                            record.get("companyName", String.class)));
        }
        return result;
    }

    /** 当前有效外部对象：未删除，且为活跃版本（历史版本 active=0）。 */
    private static Condition infoActiveCondition() {
        return intField("i", "active")
                .isNull()
                .or(intField("i", "active").eq(1))
                .and(intField("i", "deleted").eq(0));
    }

    /** 主体过滤 + 排除当前外部对象（含历史版本）。 */
    private Condition scopeCondition(
            DSLContext dsl, Long subjectId, Long excludeCompanyId, String excludeBusinessNo) {
        Condition scope = DSL.noCondition();
        if (subjectId != null) {
            scope = scope.and(longField("i", "subject_id").eq(subjectId));
        }
        String businessNo = resolveExcludeBusinessNo(dsl, excludeCompanyId, excludeBusinessNo);
        if (StringUtils.isNotBlank(businessNo)) {
            scope = scope.and(field("i", "business_no").isDistinctFrom(businessNo));
        }
        return scope;
    }

    /**
     * 解析需要排除的外部对象业务编号。
     *
     * <p>变更申请单可能只提交自身ID（或来源版本ID），此时先查其业务编号，再排除该业务编号下的全部版本。
     */
    private String resolveExcludeBusinessNo(
            DSLContext dsl, Long excludeCompanyId, String excludeBusinessNo) {
        if (StringUtils.isNotBlank(excludeBusinessNo)) {
            return excludeBusinessNo;
        }
        if (excludeCompanyId == null) {
            return null;
        }
        return dsl.select(field("business_no"))
                .from(INFO)
                .where(longField("id").eq(excludeCompanyId))
                .fetchOne(field("business_no"), String.class);
    }

    private static String columnOf(ExternalObjectQueryField field) {
        return switch (field) {
            case COMPANY_NAME -> "company_name";
            case USCC -> "uscc";
            case SHORT_NAME -> "short_name";
            case EN_FULL_NAME -> "en_full_name";
            case EN_SHORT_NAME -> "en_short_name";
        };
    }

    private static List<String> nonBlank(Collection<String> values) {
        return values.stream().filter(StringUtils::isNotBlank).toList();
    }

    private DSLContext dsl() {
        return dataSourceResolver.getDSLContext(DataSourceConstants.COMPANY);
    }

    private static Field<String> field(String name) {
        return DSL.field(DSL.name(name), String.class);
    }

    private static Field<String> field(String table, String name) {
        return DSL.field(DSL.name(table, name), String.class);
    }

    private static Field<Long> longField(String name) {
        return DSL.field(DSL.name(name), Long.class);
    }

    private static Field<Long> longField(String table, String name) {
        return DSL.field(DSL.name(table, name), Long.class);
    }

    private static Field<Integer> intField(String table, String name) {
        return DSL.field(DSL.name(table, name), Integer.class);
    }
}
