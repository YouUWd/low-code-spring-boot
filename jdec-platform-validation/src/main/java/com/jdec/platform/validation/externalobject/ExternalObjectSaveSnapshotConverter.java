package com.jdec.platform.validation.externalobject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.shared.context.AppContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 保存校验的前端参数转换器（无审批链保存、有审批链的新增保存）。
 *
 * <p>保存校验不读取业务库，直接使用前端 {@code nestedModuleData} 的行数据（键为 {@code table.column}）构造整单快照，并记录本次实际提交的模块范围：
 *
 * <ul>
 *   <li>模块按行数据的表名前缀识别，未提交的模块在快照中为空、不参与校验；
 *   <li>{@code info} 行只有 {@code info.id} 时视为未提交主档数据，不参与校验；
 *   <li>没有主单ID且未提交 {@code basic_info} 数据时，标记基本信息的所有校验规则视为不满足；
 *   <li>主体ID缺失或无效时回退到当前请求主体，保证查重范围不跨主体。
 * </ul>
 *
 * <p>词云字段（数组/对象）统一序列化为 JSON 文本，交由 {@link ExternalObjectValues} 提取真实值。
 */
@Component
@RequiredArgsConstructor
public class ExternalObjectSaveSnapshotConverter {

    /** 外部对象主档表 */
    private static final String INFO_TABLE = "info";

    /** 工商与法定资质表 */
    private static final String BASIC_INFO_TABLE = "basic_info";

    /** 组织与人员表 */
    private static final String MEMBER_TABLE = "member";

    /** 公司地址表 */
    private static final String ADDRESS_TABLE = "address";

    /** 公司联系方式表 */
    private static final String COMPANY_CONTACT_TABLE = "company_contact";

    /** 银行卡表 */
    private static final String FINANCIAL_ACCOUNT_TABLE = "financial_account";

    /** 电子账户表 */
    private static final String DIGITAL_ACCOUNT_TABLE = "digital_account";

    /** 信用等级表 */
    private static final String CREDIT_RATING_TABLE = "credit_rating";

    /** 主单ID字段：行内只有该字段时视为未提交主档数据 */
    private static final String INFO_ID_FIELD = "info.id";

    /** 参与保存校验的模块表名 */
    private static final Set<String> CHECK_TABLES =
            Set.of(
                    BASIC_INFO_TABLE,
                    MEMBER_TABLE,
                    ADDRESS_TABLE,
                    COMPANY_CONTACT_TABLE,
                    FINANCIAL_ACCOUNT_TABLE,
                    DIGITAL_ACCOUNT_TABLE,
                    CREDIT_RATING_TABLE);

    private final ObjectMapper objectMapper;

    /**
     * 转换前端提交的嵌套模块数据。
     *
     * @param nestedModuleData 保存请求中的模块嵌套数据
     * @return 保存校验输入：整单快照、本次提交的模块、基本信息缺失标记
     */
    public ExternalObjectSaveData convert(NestedModuleDataDTO nestedModuleData) {
        Map<String, List<Map<String, Object>>> tableRows = new LinkedHashMap<>();
        flatten(nestedModuleData, tableRows);

        Map<String, Object> infoRow = firstRow(tableRows, INFO_TABLE);
        Long mainId = longValue(infoRow.get(INFO_ID_FIELD));
        boolean hasMainId = mainId != null && mainId > 0;
        boolean hasBasicInfo = tableRows.containsKey(BASIC_INFO_TABLE);

        Set<String> tables = new LinkedHashSet<>();
        for (String table : tableRows.keySet()) {
            if (INFO_TABLE.equals(table)) {
                if (containsInfoData(infoRow)) {
                    tables.add(INFO_TABLE);
                }
            } else if (CHECK_TABLES.contains(table)) {
                tables.add(table);
            }
        }
        return new ExternalObjectSaveData(
                buildSnapshot(tableRows), tables, !hasMainId && !hasBasicInfo);
    }

    /** 按行数据的表名前缀收集各表的行；虚模块的空行继续向下递归。 */
    private static void flatten(
            NestedModuleDataDTO moduleData, Map<String, List<Map<String, Object>>> tableRows) {
        if (moduleData == null || moduleData.getRecord() == null) {
            return;
        }
        for (RecordDataDTO record : moduleData.getRecord()) {
            if (record == null) {
                continue;
            }
            Map<String, Object> row = record.getRow();
            if (row != null && !row.isEmpty()) {
                for (String table : rowTables(row)) {
                    tableRows.computeIfAbsent(table, key -> new ArrayList<>()).add(row);
                }
            }
            if (record.getChildren() != null) {
                for (NestedModuleDataDTO child : record.getChildren()) {
                    flatten(child, tableRows);
                }
            }
        }
    }

    /** 行数据中出现的表名（取自 {@code table.column} 键的前缀）。 */
    private static Set<String> rowTables(Map<String, Object> row) {
        Set<String> tables = new LinkedHashSet<>();
        for (String key : row.keySet()) {
            int index = key.indexOf('.');
            if (index > 0) {
                tables.add(key.substring(0, index));
            }
        }
        return tables;
    }

    private static Map<String, Object> firstRow(
            Map<String, List<Map<String, Object>>> tableRows, String table) {
        List<Map<String, Object>> rows = tableRows.get(table);
        return rows == null || rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /** 主档行是否提交了除主单ID以外的字段。 */
    private static boolean containsInfoData(Map<String, Object> infoRow) {
        for (String key : infoRow.keySet()) {
            if (key.startsWith(INFO_TABLE + ".") && !INFO_ID_FIELD.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private ExternalObjectSnapshot buildSnapshot(Map<String, List<Map<String, Object>>> tableRows) {
        return new ExternalObjectSnapshot(
                buildInfo(firstRow(tableRows, INFO_TABLE)),
                tableRows.containsKey(BASIC_INFO_TABLE)
                        ? buildBasicInfo(firstRow(tableRows, BASIC_INFO_TABLE))
                        : null,
                buildMembers(tableRows.get(MEMBER_TABLE)),
                tableRows.containsKey(ADDRESS_TABLE)
                        ? buildAddress(firstRow(tableRows, ADDRESS_TABLE))
                        : null,
                buildCompanyContacts(tableRows.get(COMPANY_CONTACT_TABLE)),
                buildFinancialAccounts(tableRows.get(FINANCIAL_ACCOUNT_TABLE)),
                buildDigitalAccounts(tableRows.get(DIGITAL_ACCOUNT_TABLE)),
                tableRows.containsKey(CREDIT_RATING_TABLE)
                        ? buildCreditRating(firstRow(tableRows, CREDIT_RATING_TABLE))
                        : null);
    }

    private ExternalObjectSnapshot.InfoRow buildInfo(Map<String, Object> row) {
        return new ExternalObjectSnapshot.InfoRow(
                longValue(row.get("info.id")),
                text(row.get("info.business_no")),
                integerValue(row.get("info.active")),
                subjectId(row),
                longValue(row.get("info.source_id")),
                text(row.get("info.company_name")),
                longValue(row.get("info.parent_id")),
                text(row.get("info.nature")),
                text(row.get("info.reg_status")),
                text(row.get("info.cooperation_types")),
                text(row.get("info.industry_tags")),
                text(row.get("info.industry_types")),
                text(row.get("info.gender")),
                integerValue(row.get("info.business_status")));
    }

    private ExternalObjectSnapshot.BasicInfoRow buildBasicInfo(Map<String, Object> row) {
        return new ExternalObjectSnapshot.BasicInfoRow(
                text(row.get("basic_info.uscc")),
                text(row.get("basic_info.id_card_no")),
                text(row.get("basic_info.legal_person")),
                integerValue(row.get("basic_info.insured_count")),
                decimalValue(row.get("basic_info.reg_capital")),
                decimalValue(row.get("basic_info.paid_in_capital")),
                dateValue(row.get("basic_info.reg_date")),
                text(row.get("basic_info.short_name")),
                text(row.get("basic_info.en_full_name")),
                text(row.get("basic_info.en_short_name")));
    }

    private List<ExternalObjectSnapshot.MemberRow> buildMembers(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ExternalObjectSnapshot.MemberRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(
                    new ExternalObjectSnapshot.MemberRow(
                            longValue(row.get("member.id")),
                            text(row.get("member.type")),
                            text(row.get("member.name")),
                            text(row.get("member.member_name")),
                            text(row.get("member.mobile_phone_no")),
                            text(row.get("member.identity_no"))));
        }
        return result;
    }

    private ExternalObjectSnapshot.AddressRow buildAddress(Map<String, Object> row) {
        return new ExternalObjectSnapshot.AddressRow(
                text(row.get("address.comm_region")),
                text(row.get("address.comm_detail")),
                text(row.get("address.reg_region")),
                text(row.get("address.reg_detail")));
    }

    private List<ExternalObjectSnapshot.CompanyContactRow> buildCompanyContacts(
            List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ExternalObjectSnapshot.CompanyContactRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(
                    new ExternalObjectSnapshot.CompanyContactRow(
                            longValue(row.get("company_contact.id")),
                            text(row.get("company_contact.contact_name")),
                            text(row.get("company_contact.contact_type")),
                            text(row.get("company_contact.contact_value"))));
        }
        return result;
    }

    private List<ExternalObjectSnapshot.FinancialAccountRow> buildFinancialAccounts(
            List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ExternalObjectSnapshot.FinancialAccountRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(
                    new ExternalObjectSnapshot.FinancialAccountRow(
                            longValue(row.get("financial_account.id")),
                            text(row.get("financial_account.account_status"))));
        }
        return result;
    }

    private List<ExternalObjectSnapshot.DigitalAccountRow> buildDigitalAccounts(
            List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ExternalObjectSnapshot.DigitalAccountRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(
                    new ExternalObjectSnapshot.DigitalAccountRow(
                            longValue(row.get("digital_account.id")),
                            text(row.get("digital_account.account_status"))));
        }
        return result;
    }

    private ExternalObjectSnapshot.CreditRatingRow buildCreditRating(Map<String, Object> row) {
        return new ExternalObjectSnapshot.CreditRatingRow(
                text(row.get("credit_rating.internal_level")),
                text(row.get("credit_rating.qcc_score")));
    }

    /**
     * 主体ID：前端提交的值无效时回退到当前请求主体。
     *
     * <p>保存请求可能不传主体ID（或传 0），而查重范围需要按主体过滤，避免跨主体误判重复。
     */
    private static Long subjectId(Map<String, Object> row) {
        Long subjectId = longValue(row.get("info.subject_id"));
        if (subjectId != null && subjectId > 0) {
            return subjectId;
        }
        Long currentSubjectId = AppContext.getSubjectId();
        return currentSubjectId != null && currentSubjectId > 0 ? currentSubjectId : subjectId;
    }

    /** 文本字段：词云等结构化值序列化为 JSON 文本，普通值取字符串形式。 */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate dateValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        String text = String.valueOf(value).trim();
        if (text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
