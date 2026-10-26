package com.jdec.platform.externalobject.biz.service.support;

import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 外部对象实时校验/查重的数据库读取入口。
 *
 * <p>直连 {@code company} 业务库，按主单（info）读取对象性质与业务编号，按人员ID读取人员关键字段， 并为人员组合查重收集比对人员。
 */
@Component
@RequiredArgsConstructor
public class ExternalObjectRealtimeRepository {

    private final DataSourceResolver dataSourceResolver;

    /** company 业务库的 DSLContext。 */
    public DSLContext dsl() {
        return dataSourceResolver.getDSLContext(DataSourceConstants.COMPANY);
    }

    /** 读取 info.nature 原始值（JSON 字符串）。 */
    public String findInfoNature(Long infoId) {
        if (infoId == null) {
            return null;
        }
        DSLContext dsl = dsl();
        return dsl.select(ExternalObjectTable.INFO.textField("nature"))
                .from(ExternalObjectTable.INFO.table())
                .where(ExternalObjectTable.INFO.longField("id").eq(infoId))
                .and(ExternalObjectTable.INFO.intField("deleted").eq(0))
                .fetchOne(ExternalObjectTable.INFO.textField("nature"));
    }

    /** 读取外部对象业务编号；不存在或已删除返回 {@code null}。 */
    public String findBusinessNo(Long infoId) {
        return findInfoText(infoId, "business_no");
    }

    /** 同一业务编号下的全部主单ID（含历史版本），用于排除自身外部对象。 */
    public Set<Long> findInfoIdsByBusinessNo(String businessNo) {
        if (StringUtils.isBlank(businessNo)) {
            return Set.of();
        }
        return new LinkedHashSet<>(
                dsl().select(ExternalObjectTable.INFO.longField("id"))
                        .from(ExternalObjectTable.INFO.table())
                        .where(ExternalObjectTable.INFO.textField("business_no").eq(businessNo))
                        .fetch(ExternalObjectTable.INFO.longField("id")));
    }

    private String findInfoText(Long infoId, String column) {
        if (infoId == null) {
            return null;
        }
        return dsl().select(ExternalObjectTable.INFO.textField(column))
                .from(ExternalObjectTable.INFO.table())
                .where(ExternalObjectTable.INFO.longField("id").eq(infoId))
                .and(ExternalObjectTable.INFO.intField("deleted").eq(0))
                .fetchOne(ExternalObjectTable.INFO.textField(column));
    }

    /**
     * 按请求上下文解析当前外部对象的对象性质。
     *
     * <p>子表未传主单ID时，先通过子单行反查其 company_id。
     */
    public String findInfoNature(ExternalObjectTable table, Long companyId, Long subId) {
        Long infoId = resolveInfoId(table, companyId, subId);
        return findInfoNature(infoId);
    }

    /** 读取行记录所属的主单ID；info 直接返回自身，子表读取 company_id。 */
    public Long findCompanyId(ExternalObjectTable table, Long rowId) {
        if (rowId == null) {
            return null;
        }
        if (table == ExternalObjectTable.INFO) {
            return rowId;
        }
        DSLContext dsl = dsl();
        return dsl.select(table.longField("company_id"))
                .from(table.table())
                .where(table.longField("id").eq(rowId))
                .and(table.intField("deleted").eq(0))
                .fetchOne(table.longField("company_id"));
    }

    /** 解析当前外部对象主单ID。 */
    public Long resolveInfoId(ExternalObjectTable table, Long companyId, Long subId) {
        if (table == ExternalObjectTable.INFO) {
            return subId != null ? subId : companyId;
        }
        if (companyId != null) {
            return companyId;
        }
        return findCompanyId(table, subId);
    }

    /** 读取人员行关键字段；不存在或已删除返回 {@code null}。 */
    public MemberRow findMember(Long memberId) {
        if (memberId == null) {
            return null;
        }
        DSLContext dsl = dsl();
        Record record =
                dsl.select(
                                ExternalObjectTable.MEMBER.longField("id"),
                                ExternalObjectTable.MEMBER.textField("name"),
                                ExternalObjectTable.MEMBER.textField("mobile_phone_no"),
                                ExternalObjectTable.MEMBER.textField("identity_no"),
                                ExternalObjectTable.MEMBER.textField("type"))
                        .from(ExternalObjectTable.MEMBER.table())
                        .where(ExternalObjectTable.MEMBER.longField("id").eq(memberId))
                        .and(ExternalObjectTable.MEMBER.intField("deleted").eq(0))
                        .fetchOne();
        return record == null ? null : toMember(record);
    }

    /**
     * 在给定人员行范围内，按姓名、手机号、证件号任一命中收集比对人员。
     *
     * <p>组合判断在调用方完成，这里只做数据库侧预筛；空值字段不参与预筛条件。姓名同时匹配 {@code name} 与 {@code
     * member_name}，与保存校验的人员比对口径保持一致。
     *
     * @param memberIds 人员行范围（已按查重范围收集）
     * @param name 姓名
     * @param mobilePhoneNo 手机号
     * @param identityNo 证件号
     */
    public List<MemberCandidate> findMemberCandidates(
            Collection<Long> memberIds, String name, String mobilePhoneNo, String identityNo) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        Condition anyMatch = null;
        if (StringUtils.isNotBlank(name)) {
            anyMatch =
                    DSL.trim(memberField("name"))
                            .eq(name)
                            .or(DSL.trim(memberField("member_name")).eq(name));
        }
        if (StringUtils.isNotBlank(mobilePhoneNo)) {
            Condition mobileMatch = DSL.trim(memberField("mobile_phone_no")).eq(mobilePhoneNo);
            anyMatch = anyMatch == null ? mobileMatch : anyMatch.or(mobileMatch);
        }
        if (StringUtils.isNotBlank(identityNo)) {
            Condition identityMatch =
                    DSL.upper(DSL.trim(memberField("identity_no")))
                            .eq(identityNo.toUpperCase(Locale.ROOT));
            anyMatch = anyMatch == null ? identityMatch : anyMatch.or(identityMatch);
        }
        if (anyMatch == null) {
            return List.of();
        }

        Table<?> member = ExternalObjectTable.MEMBER.table().as("m");
        Table<?> info = ExternalObjectTable.INFO.table().as("i");
        return dsl()
                .select(
                        DSL.field(DSL.name("m", "id"), Long.class).as("row_id"),
                        DSL.field(DSL.name("m", "company_id"), Long.class).as("row_company_id"),
                        DSL.field(DSL.name("i", "company_name"), String.class).as("info_name"),
                        DSL.field(DSL.name("m", "name"), String.class).as("row_name"),
                        DSL.field(DSL.name("m", "member_name"), String.class).as("row_member_name"),
                        DSL.field(DSL.name("m", "mobile_phone_no"), String.class).as("row_mobile"),
                        DSL.field(DSL.name("m", "identity_no"), String.class).as("row_identity"),
                        DSL.field(DSL.name("m", "type"), String.class).as("row_type"))
                .from(member)
                .join(info)
                .on(
                        DSL.field(DSL.name("m", "company_id"), Long.class)
                                .eq(DSL.field(DSL.name("i", "id"), Long.class)))
                .where(DSL.field(DSL.name("m", "id"), Long.class).in(memberIds))
                .and(DSL.field(DSL.name("m", "deleted"), Integer.class).eq(0))
                .and(anyMatch)
                .fetch()
                .stream()
                .map(
                        record ->
                                new MemberCandidate(
                                        record.get("row_id", Long.class),
                                        record.get("row_company_id", Long.class),
                                        record.get("info_name", String.class),
                                        StringUtils.trimToEmpty(
                                                record.get("row_name", String.class)),
                                        StringUtils.trimToEmpty(
                                                record.get("row_member_name", String.class)),
                                        StringUtils.trimToEmpty(
                                                record.get("row_mobile", String.class)),
                                        StringUtils.trimToEmpty(
                                                record.get("row_identity", String.class)),
                                        record.get("row_type", String.class)))
                .toList();
    }

    private static Field<String> memberField(String column) {
        return DSL.field(DSL.name("m", column), String.class);
    }

    private static MemberRow toMember(Record record) {
        return new MemberRow(
                record.get(ExternalObjectTable.MEMBER.longField("id")),
                StringUtils.trimToEmpty(record.get(ExternalObjectTable.MEMBER.textField("name"))),
                StringUtils.trimToEmpty(
                        record.get(ExternalObjectTable.MEMBER.textField("mobile_phone_no"))),
                StringUtils.trimToEmpty(
                        record.get(ExternalObjectTable.MEMBER.textField("identity_no"))),
                record.get(ExternalObjectTable.MEMBER.textField("type")));
    }

    /**
     * 人员行关键字段。
     *
     * @param id 人员记录ID
     * @param name 姓名
     * @param mobilePhoneNo 手机号
     * @param identityNo 证件号
     * @param type 节点类型（EMPLOYEE/DEPT 或 员工/部门）
     */
    public record MemberRow(
            Long id, String name, String mobilePhoneNo, String identityNo, String type) {}

    /**
     * 人员查重的比对人员。
     *
     * @param id 人员记录ID
     * @param companyId 所属外部对象主单ID
     * @param companyName 所属外部对象全称
     * @param name 姓名（{@code name} 列）
     * @param memberName 姓名兼称（{@code member_name} 列，{@code name} 为空时使用）
     * @param mobilePhoneNo 手机号
     * @param identityNo 证件号
     * @param type 节点类型（EMPLOYEE/DEPT 或 员工/部门）
     */
    public record MemberCandidate(
            Long id,
            Long companyId,
            String companyName,
            String name,
            String memberName,
            String mobilePhoneNo,
            String identityNo,
            String type) {}
}
