package com.jdec.platform.validation.externalobject;

import com.jdec.platform.shared.datasource.DataSourceConstants;
import com.jdec.platform.shared.datasource.DataSourceResolver;
import com.jdec.platform.shared.exception.ApiCodeEnum;
import com.jdec.platform.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 外部对象提交校验的数据读取入口。
 *
 * <p>直连 {@code company} 业务库，按主单（{@code info.id}）读取外部对象信息申请涉及的整单数据，并为查重收集已保存数据：
 *
 * <ul>
 *   <li>整单数据：info、basic_info、member、address、company_contact、financial_account、digital_account、credit_rating；
 *   <li>查重候选：{@code active=1} 的外部对象；尚未终结（业务状态不是 99/-99）的，再补充同业务编号下最后一个启用版本。
 * </ul>
 *
 * <p>定位不到校验对象时返回 {@link ApiCodeEnum#WARNING_EXISTS}（702），与其它业务异常的 700 区分。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalObjectSnapshotRepository {

    private static final Table<?> INFO = table("info");
    private static final Table<?> BASIC_INFO = table("basic_info");
    private static final Table<?> MEMBER = table("member");
    private static final Table<?> ADDRESS = table("address");
    private static final Table<?> COMPANY_CONTACT = table("company_contact");
    private static final Table<?> FINANCIAL_ACCOUNT = table("financial_account");
    private static final Table<?> DIGITAL_ACCOUNT = table("digital_account");
    private static final Table<?> CREDIT_RATING = table("credit_rating");

    private final DataSourceResolver dataSourceResolver;

    /** company 业务库的 DSLContext。 */
    public DSLContext dsl() {
        return dataSourceResolver.getDSLContext(DataSourceConstants.COMPANY);
    }

    /**
     * 按主单ID加载外部对象整单数据。
     *
     * @param companyId 外部对象主单ID（info.id）
     * @return 整单数据快照
     * @throws BusinessException 主单不存在或已删除
     */
    public ExternalObjectSnapshot load(Long companyId) {
        DSLContext dsl = dsl();
        Record infoRecord =
                dsl.selectFrom(INFO)
                        .where(longField("info", "id").eq(companyId))
                        .and(intField("info", "deleted").eq(0))
                        .fetchOne();
        if (infoRecord == null) {
            throw new BusinessException(ApiCodeEnum.WARNING_EXISTS.getCode(), "外部对象不存在或已删除");
        }
        return toSnapshot(dsl, infoRecord);
    }

    /**
     * 按业务编号与版本号加载本次提交保存后的外部对象整单数据。
     *
     * <p>变更申请保存时会生成新版本记录，提交接口返回的主单ID仍指向原记录，因此需要用业务编号+版本号定位本次提交的数据。
     *
     * @param businessNo 业务编号
     * @param versionNo 版本号
     * @return 整单数据快照
     * @throws BusinessException 主单不存在或已删除
     */
    public ExternalObjectSnapshot load(String businessNo, Integer versionNo) {
        if (businessNo == null || businessNo.isBlank() || versionNo == null) {
            throw new BusinessException(ApiCodeEnum.WARNING_EXISTS.getCode(), "外部对象业务编号或版本号不能为空");
        }
        DSLContext dsl = dsl();
        Record infoRecord =
                dsl.selectFrom(INFO)
                        .where(textField("info", "business_no").eq(businessNo))
                        .and(intField("info", "version_no").eq(versionNo))
                        .and(intField("info", "deleted").eq(0))
                        .orderBy(longField("info", "id").desc())
                        .limit(1)
                        .fetchOne();
        if (infoRecord == null) {
            throw new BusinessException(ApiCodeEnum.WARNING_EXISTS.getCode(), "外部对象不存在或已删除");
        }
        return toSnapshot(dsl, infoRecord);
    }

    private ExternalObjectSnapshot toSnapshot(DSLContext dsl, Record infoRecord) {
        Long companyId = infoRecord.get("id", Long.class);
        ExternalObjectSnapshot.InfoRow info =
                new ExternalObjectSnapshot.InfoRow(
                        companyId,
                        infoRecord.get("business_no", String.class),
                        infoRecord.get("active", Integer.class),
                        infoRecord.get("subject_id", Long.class),
                        infoRecord.get("source_id", Long.class),
                        infoRecord.get("company_name", String.class),
                        infoRecord.get("parent_id", Long.class),
                        infoRecord.get("nature", String.class),
                        infoRecord.get("reg_status", String.class),
                        infoRecord.get("cooperation_types", String.class),
                        infoRecord.get("industry_tags", String.class),
                        infoRecord.get("industry_types", String.class),
                        infoRecord.get("gender", String.class),
                        infoRecord.get("business_status", Integer.class));

        return new ExternalObjectSnapshot(
                info,
                findBasicInfo(dsl, companyId),
                findMembers(dsl, companyId),
                findAddress(dsl, companyId),
                findCompanyContacts(dsl, companyId),
                findFinancialAccounts(dsl, companyId),
                findDigitalAccounts(dsl, companyId),
                findCreditRating(dsl, companyId));
    }

    /**
     * 收集参与查重的已保存外部对象主档。
     *
     * <p>范围为未删除的记录中：正在走流程（{@code active=1} 且 business_status 非 99/-99，业务状态为空亦视为在流程中）
     * 与最后启用（business_status=99 且 {@code active=1}，即当前生效的启用记录）。business_status=-99（已终结/作废）与
     * business_status=99 但 {@code active=0}（已不再生效的上一启用版本）不参与查重。查询失败时记录日志并返回空列表，不阻断提交校验。
     *
     * @param subjectId 主体ID；为空时不按主体过滤
     */
    public List<ExternalObjectSnapshot.InfoBrief> findDuplicateInfos(Long subjectId) {
        try {
            DSLContext dsl = dsl();
            Condition condition = inProcessInfoCondition().or(lastEnabledInfoCondition());
            condition = condition.and(intField("info", "deleted").eq(0));
            if (subjectId != null && subjectId > 0) {
                condition = condition.and(longField("info", "subject_id").eq(subjectId));
            }
            var records = dsl.selectFrom(INFO).where(condition).fetch();
            List<ExternalObjectSnapshot.InfoBrief> result = new ArrayList<>();
            for (Record record : records) {
                result.add(
                        new ExternalObjectSnapshot.InfoBrief(
                                record.get("id", Long.class),
                                record.get("business_no", String.class),
                                record.get("company_name", String.class)));
            }
            return result;
        } catch (Exception e) {
            log.warn("外部对象查重范围查询失败，已跳过查重", e);
            return List.of();
        }
    }

    /** 读取查重候选的基本信息（basic_info）。 */
    public List<ExternalObjectSnapshot.BasicInfoBrief> findDuplicateBasicInfos(
            Collection<Long> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return List.of();
        }
        try {
            List<Record> records =
                    dsl().select(
                                    BASIC_INFO.asterisk(),
                                    textField("info", "company_name").as("info_company_name"))
                            .from(BASIC_INFO)
                            .join(INFO)
                            .on(longField("basic_info", "company_id").eq(longField("info", "id")))
                            .where(longField("basic_info", "company_id").in(companyIds))
                            .and(intField("basic_info", "deleted").eq(0))
                            .fetch();
            return records.stream()
                    .map(
                            record ->
                                    new ExternalObjectSnapshot.BasicInfoBrief(
                                            record.get("company_id", Long.class),
                                            record.get("info_company_name", String.class),
                                            record.get("uscc", String.class),
                                            record.get("short_name", String.class),
                                            record.get("en_full_name", String.class),
                                            record.get("en_short_name", String.class)))
                    .toList();
        } catch (Exception e) {
            log.warn("外部对象基本信息查重查询失败，已跳过", e);
            return List.of();
        }
    }

    /** 读取查重候选的人员（member）。 */
    public List<ExternalObjectSnapshot.MemberBrief> findDuplicateMembers(
            Collection<Long> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return List.of();
        }
        try {
            List<Record> records =
                    dsl().select(
                                    MEMBER.asterisk(),
                                    textField("info", "company_name").as("info_company_name"))
                            .from(MEMBER)
                            .join(INFO)
                            .on(longField("member", "company_id").eq(longField("info", "id")))
                            .where(longField("member", "company_id").in(companyIds))
                            .and(intField("member", "deleted").eq(0))
                            .fetch();
            return records.stream()
                    .map(
                            record ->
                                    new ExternalObjectSnapshot.MemberBrief(
                                            record.get("company_id", Long.class),
                                            record.get("info_company_name", String.class),
                                            record.get("name", String.class),
                                            record.get("member_name", String.class),
                                            record.get("mobile_phone_no", String.class),
                                            record.get("identity_no", String.class)))
                    .toList();
        } catch (Exception e) {
            log.warn("外部对象人员查重查询失败，已跳过", e);
            return List.of();
        }
    }

    /**
     * 读取指定外部对象已保存的人员行。
     *
     * <p>保存校验只拿得到前端提交的人员行，需要与库中同一主单已保存的人员行合并后再做组合查重。 查询失败时记录日志并返回空列表， 不阻断保存。
     *
     * @param companyId 外部对象主单ID（info.id）
     */
    public List<ExternalObjectSnapshot.MemberRow> findMembersOfCompany(Long companyId) {
        if (companyId == null || companyId <= 0) {
            return List.of();
        }
        try {
            return findMembers(dsl(), companyId);
        } catch (Exception e) {
            log.warn("外部对象人员行查询失败，已跳过与库中数据合并", e);
            return List.of();
        }
    }

    private ExternalObjectSnapshot.BasicInfoRow findBasicInfo(DSLContext dsl, Long companyId) {
        Record record =
                dsl.selectFrom(BASIC_INFO)
                        .where(longField("basic_info", "company_id").eq(companyId))
                        .and(intField("basic_info", "deleted").eq(0))
                        .fetchOne();
        if (record == null) {
            return null;
        }
        return new ExternalObjectSnapshot.BasicInfoRow(
                record.get("uscc", String.class),
                record.get("id_card_no", String.class),
                record.get("legal_person", String.class),
                record.get("insured_count", Integer.class),
                record.get("reg_capital", BigDecimal.class),
                record.get("paid_in_capital", BigDecimal.class),
                record.get("reg_date", LocalDate.class),
                record.get("short_name", String.class),
                record.get("en_full_name", String.class),
                record.get("en_short_name", String.class));
    }

    private List<ExternalObjectSnapshot.MemberRow> findMembers(DSLContext dsl, Long companyId) {
        return dsl
                .selectFrom(MEMBER)
                .where(longField("member", "company_id").eq(companyId))
                .and(intField("member", "deleted").eq(0))
                .orderBy(intField("member", "sort_order").asc(), longField("member", "id").asc())
                .fetch()
                .stream()
                .map(
                        record ->
                                new ExternalObjectSnapshot.MemberRow(
                                        record.get("id", Long.class),
                                        record.get("type", String.class),
                                        record.get("name", String.class),
                                        record.get("member_name", String.class),
                                        record.get("mobile_phone_no", String.class),
                                        record.get("identity_no", String.class),
                                        record.get("gender", String.class)))
                .toList();
    }

    private ExternalObjectSnapshot.AddressRow findAddress(DSLContext dsl, Long companyId) {
        Record record =
                dsl.selectFrom(ADDRESS)
                        .where(longField("address", "company_id").eq(companyId))
                        .and(intField("address", "deleted").eq(0))
                        .fetchOne();
        if (record == null) {
            return null;
        }
        return new ExternalObjectSnapshot.AddressRow(
                record.get("comm_region", String.class),
                record.get("comm_detail", String.class),
                record.get("reg_region", String.class),
                record.get("reg_detail", String.class));
    }

    private List<ExternalObjectSnapshot.CompanyContactRow> findCompanyContacts(
            DSLContext dsl, Long companyId) {
        return dsl
                .selectFrom(COMPANY_CONTACT)
                .where(longField("company_contact", "company_id").eq(companyId))
                .and(intField("company_contact", "deleted").eq(0))
                .orderBy(
                        intField("company_contact", "sort_order").asc(),
                        longField("company_contact", "id").asc())
                .fetch()
                .stream()
                .map(
                        record ->
                                new ExternalObjectSnapshot.CompanyContactRow(
                                        record.get("id", Long.class),
                                        record.get("contact_name", String.class),
                                        record.get("contact_type", String.class),
                                        record.get("contact_value", String.class)))
                .toList();
    }

    private List<ExternalObjectSnapshot.FinancialAccountRow> findFinancialAccounts(
            DSLContext dsl, Long companyId) {
        return dsl
                .selectFrom(FINANCIAL_ACCOUNT)
                .where(longField("financial_account", "company_id").eq(companyId))
                .and(intField("financial_account", "deleted").eq(0))
                .orderBy(
                        intField("financial_account", "sort_order").asc(),
                        longField("financial_account", "id").asc())
                .fetch()
                .stream()
                .map(
                        record ->
                                new ExternalObjectSnapshot.FinancialAccountRow(
                                        record.get("id", Long.class),
                                        record.get("account_status", String.class)))
                .toList();
    }

    private List<ExternalObjectSnapshot.DigitalAccountRow> findDigitalAccounts(
            DSLContext dsl, Long companyId) {
        return dsl
                .selectFrom(DIGITAL_ACCOUNT)
                .where(longField("digital_account", "company_id").eq(companyId))
                .and(intField("digital_account", "deleted").eq(0))
                .orderBy(
                        intField("digital_account", "sort_order").asc(),
                        longField("digital_account", "id").asc())
                .fetch()
                .stream()
                .map(
                        record ->
                                new ExternalObjectSnapshot.DigitalAccountRow(
                                        record.get("id", Long.class),
                                        record.get("account_status", String.class)))
                .toList();
    }

    private ExternalObjectSnapshot.CreditRatingRow findCreditRating(
            DSLContext dsl, Long companyId) {
        Record record =
                dsl.selectFrom(CREDIT_RATING)
                        .where(longField("credit_rating", "company_id").eq(companyId))
                        .and(intField("credit_rating", "deleted").eq(0))
                        .fetchOne();
        if (record == null) {
            return null;
        }
        return new ExternalObjectSnapshot.CreditRatingRow(
                record.get("internal_level", String.class), record.get("qcc_score", String.class));
    }

    /** 正在走流程：active=1 且 business_status 不是 99/-99（business_status 为空也视为在流程中）。 */
    private static Condition inProcessInfoCondition() {
        return intField("info", "active")
                .eq(1)
                .and(
                        intField("info", "business_status")
                                .isNull()
                                .or(intField("info", "business_status").notIn(99, -99)));
    }

    /** 最后启用：business_status=99 且 active=1（当前生效的启用记录）。 */
    private static Condition lastEnabledInfoCondition() {
        return intField("info", "business_status").eq(99).and(intField("info", "active").eq(1));
    }

    private static Table<?> table(String name) {
        return DSL.table(DSL.name(name));
    }

    private static Field<Long> longField(String table, String column) {
        return DSL.field(DSL.name(table, column), Long.class);
    }

    private static Field<String> textField(String table, String column) {
        return DSL.field(DSL.name(table, column), String.class);
    }

    private static Field<Integer> intField(String table, String column) {
        return DSL.field(DSL.name(table, column), Integer.class);
    }
}
