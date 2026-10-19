package com.jdec.platform.externalobject.biz.validation;

import com.jdec.platform.shared.validation.dynamic.ValidationError;
import java.util.Map;

/**
 * 外部对象表单字段中文名与页签名称。
 *
 * <p>字段标识统一使用 {@code 表名.列名}，与请求 row 中的键保持一致。
 */
public final class ExternalObjectFieldLabels {

    private static final Map<String, String> FIELD_LABELS =
            Map.ofEntries(
                    // info
                    Map.entry(fieldKey(ExternalObjectModules.INFO, "info.company_name"), "外部对象全称"),
                    Map.entry(fieldKey(ExternalObjectModules.INFO, "info.nature"), "对象性质"),
                    Map.entry(fieldKey(ExternalObjectModules.INFO, "info.gender"), "性别"),
                    Map.entry(fieldKey(ExternalObjectModules.INFO, "info.reg_status"), "登记状态"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.INFO, "info.cooperation_types"), "合作类型"),
                    Map.entry(fieldKey(ExternalObjectModules.INFO, "info.parent_id"), "外部对象父级全称"),
                    Map.entry(fieldKey(ExternalObjectModules.INFO, "info.industry_tags"), "行业标签"),
                    Map.entry(fieldKey(ExternalObjectModules.INFO, "info.industry_types"), "行业类型"),
                    Map.entry(fieldKey(ExternalObjectModules.INFO, "info.logo_url"), "Logo/头像"),
                    // basic_info
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.uscc"),
                            "统一社会信用代码"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.id_card_no"),
                            "身份证号"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.short_name"),
                            "外部对象简称"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.en_full_name"),
                            "外部对象英文全称"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.en_short_name"),
                            "外部对象英文简称"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.former_name"),
                            "外部对象曾用名全称"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.BASIC_INFO,
                                    "basic_info.former_short_name"),
                            "外部对象曾用名简称"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.en_former_name"),
                            "外部对象英文曾用名全称"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.BASIC_INFO,
                                    "basic_info.en_former_short_name"),
                            "外部对象英文曾用名简称"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.BASIC_INFO,
                                    "basic_info.taxpayer_qualification"),
                            "纳税人资质"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.website"), "网址"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.legal_person"),
                            "法定代表人/负责人"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.BASIC_INFO, "basic_info.economic_nature"),
                            "经济性质"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.insured_count"),
                            "参保人数"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.reg_capital"),
                            "注册资本"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.BASIC_INFO, "basic_info.paid_in_capital"),
                            "实缴资本"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.company_type"),
                            "公司类型"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.reg_date"),
                            "注册登记日期"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.BASIC_INFO,
                                    "basic_info.business_term_start"),
                            "营业期限"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.BASIC_INFO,
                                    "basic_info.business_term_end"),
                            "营业期限"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.date_of_birth"),
                            "出生年月"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.BASIC_INFO,
                                    "basic_info.id_card_front_url"),
                            "身份证件照片"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.BASIC_INFO,
                                    "basic_info.id_card_back_url"),
                            "身份证件照片"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.BASIC_INFO, "basic_info.license_url"),
                            "主体登记证照"),
                    // member
                    Map.entry(fieldKey(ExternalObjectModules.MEMBER, "member.type"), "类型"),
                    Map.entry(fieldKey(ExternalObjectModules.MEMBER, "member.name"), "部门名称/姓名"),
                    Map.entry(fieldKey(ExternalObjectModules.MEMBER, "member.code"), "外部部门号"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.MEMBER, "member.dept_leader_flag"),
                            "部门负责人"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.MEMBER, "member.identity_type"), "证件类型"),
                    Map.entry(fieldKey(ExternalObjectModules.MEMBER, "member.identity_no"), "证件号"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.MEMBER, "member.mobile_phone_no"),
                            "手机号码"),
                    Map.entry(fieldKey(ExternalObjectModules.MEMBER, "member.work_field"), "工作领域"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.MEMBER, "member.expertise_field"),
                            "擅长领域"),
                    Map.entry(fieldKey(ExternalObjectModules.MEMBER, "member.photo_url"), "上传照片"),
                    Map.entry(fieldKey(ExternalObjectModules.MEMBER, "member.position"), "职务"),
                    Map.entry(fieldKey(ExternalObjectModules.MEMBER, "member.status"), "在职状态"),
                    Map.entry(fieldKey(ExternalObjectModules.MEMBER, "member.gender"), "性别"),
                    // member_contact
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.MEMBER_CONTACT,
                                    "member_contact.contact_type"),
                            "联系方式"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.MEMBER_CONTACT,
                                    "member_contact.contact_value"),
                            "号码"),
                    // address
                    Map.entry(
                            fieldKey(ExternalObjectModules.ADDRESS, "address.comm_detail"), "通讯地址"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.ADDRESS, "address.comm_postcode"),
                            "邮政编码"),
                    Map.entry(
                            fieldKey(ExternalObjectModules.ADDRESS, "address.reg_detail"), "注册地址"),
                    // company_contact
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.COMPANY_CONTACT,
                                    "company_contact.contact_name"),
                            "联系人"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.COMPANY_CONTACT,
                                    "company_contact.contact_type"),
                            "联系方式"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.COMPANY_CONTACT,
                                    "company_contact.contact_value"),
                            "号码"),
                    // financial_account
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.FINANCIAL_ACCOUNT,
                                    "financial_account.account_no"),
                            "账号"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.FINANCIAL_ACCOUNT,
                                    "financial_account.account_name"),
                            "账户名"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.FINANCIAL_ACCOUNT,
                                    "financial_account.bank_head"),
                            "行别"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.FINANCIAL_ACCOUNT,
                                    "financial_account.bank_branch"),
                            "开户行"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.FINANCIAL_ACCOUNT,
                                    "financial_account.account_status"),
                            "账号状态"),
                    // digital_account
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.DIGITAL_ACCOUNT,
                                    "digital_account.platform"),
                            "平台"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.DIGITAL_ACCOUNT,
                                    "digital_account.account_no"),
                            "账号"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.DIGITAL_ACCOUNT,
                                    "digital_account.real_name"),
                            "真实姓名"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.DIGITAL_ACCOUNT,
                                    "digital_account.account_status"),
                            "账号状态"),
                    // credit_rating
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.CREDIT_RATING,
                                    "credit_rating.internal_level"),
                            "内部信用等级"),
                    Map.entry(
                            fieldKey(
                                    ExternalObjectModules.CREDIT_RATING, "credit_rating.qcc_score"),
                            "企查查评分"),
                    // venue_line / venue / line
                    Map.entry(
                            fieldKey(ExternalObjectModules.VENUE_LINE, "venue_line.company_name"),
                            "公司名称"),
                    Map.entry(fieldKey(ExternalObjectModules.VENUE, "venue.name"), "场地名称"),
                    Map.entry(fieldKey(ExternalObjectModules.VENUE, "venue.address"), "场地位置"),
                    Map.entry(fieldKey(ExternalObjectModules.VENUE, "venue.description"), "场地描述"),
                    Map.entry(fieldKey(ExternalObjectModules.VENUE, "venue.image"), "场地照片"),
                    Map.entry(fieldKey(ExternalObjectModules.LINE, "line.name"), "产线名称"),
                    Map.entry(fieldKey(ExternalObjectModules.LINE, "line.person"), "产线负责人"),
                    Map.entry(fieldKey(ExternalObjectModules.LINE, "line.phone"), "联系电话"),
                    Map.entry(fieldKey(ExternalObjectModules.LINE, "line.description"), "产线描述"));

    private static final Map<String, String> TAB_NAMES =
            Map.ofEntries(
                    Map.entry(
                            ExternalObjectModules.BASIC_INFO, ExternalObjectModules.TAB_BASIC_INFO),
                    Map.entry(ExternalObjectModules.MEMBER, ExternalObjectModules.TAB_MEMBER),
                    Map.entry(
                            ExternalObjectModules.MEMBER_CONTACT, ExternalObjectModules.TAB_MEMBER),
                    Map.entry(ExternalObjectModules.ADDRESS, ExternalObjectModules.TAB_CONTACT),
                    Map.entry(
                            ExternalObjectModules.COMPANY_CONTACT,
                            ExternalObjectModules.TAB_CONTACT),
                    Map.entry(
                            ExternalObjectModules.FINANCIAL_ACCOUNT,
                            ExternalObjectModules.TAB_FINANCE),
                    Map.entry(
                            ExternalObjectModules.DIGITAL_ACCOUNT,
                            ExternalObjectModules.TAB_FINANCE),
                    Map.entry(
                            ExternalObjectModules.CREDIT_RATING,
                            ExternalObjectModules.TAB_CREDIT_RATING),
                    Map.entry(ExternalObjectModules.VENUE, ExternalObjectModules.TAB_VENUE),
                    Map.entry(ExternalObjectModules.LINE, ExternalObjectModules.TAB_LINE));

    private ExternalObjectFieldLabels() {}

    /**
     * 返回错误对应的中文名称。
     *
     * <p>子级字段格式为 {@code 菜单名-字段中文名}，例如 {@code 基本信息-统一社会信用代码}、{@code 人员构成-手机号码}。
     */
    public static String displayName(ValidationError error) {
        String label = FIELD_LABELS.get(fieldKey(error.moduleId(), error.fieldKey()));
        if (label == null) {
            return error.fieldKey();
        }
        if (!isChild(error.path())) {
            return label;
        }
        String tabName = TAB_NAMES.get(error.moduleId());
        return tabName == null ? label : tabName + "-" + label;
    }

    private static boolean isChild(String path) {
        return path != null && path.contains(".children[");
    }

    private static String fieldKey(String moduleId, String fieldKey) {
        return moduleId + "|" + fieldKey;
    }
}
