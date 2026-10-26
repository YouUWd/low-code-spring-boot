package com.jdec.platform.externalobject.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 外部对象工商与法定资质表（Tab 1 基本信息）
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("basic_info")
public class BasicInfo extends Model<BasicInfo> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 数据编码 */
    @TableField("business_no")
    private String businessNo;

    /** 版本号 */
    @TableField("version_no")
    private Integer versionNo;

    /** 主体ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 所属公司ID（关联 info.id） */
    @TableField("company_id")
    private Long companyId;

    /** 统一社会信用代码 */
    @TableField("uscc")
    private String uscc;

    /** 外部对象简称 */
    @TableField("short_name")
    private String shortName;

    /** 外部对象英文简称 */
    @TableField("en_short_name")
    private String enShortName;

    /** 外部对象曾用名全称 */
    @TableField("former_name")
    private String formerName;

    /** 外部对象曾用名简称 */
    @TableField("former_short_name")
    private String formerShortName;

    /** 外部对象英文曾用名全称 */
    @TableField("en_former_name")
    private String enFormerName;

    /** 外部对象英文曾用名简称 */
    @TableField("en_former_short_name")
    private String enFormerShortName;

    /** 纳税人资质（如: 一般纳税人/小规模纳税人） */
    @TableField("taxpayer_qualification")
    private String taxpayerQualification;

    /** 公司网址 */
    @TableField("website")
    private String website;

    /** 法定代表人/负责人 */
    @TableField("legal_person")
    private String legalPerson;

    /** 经济性质（如: 国有企业/民营企业） */
    @TableField("economic_nature")
    private String economicNature;

    /** 参保人数 */
    @TableField("insured_count")
    private Integer insuredCount;

    /** 注册资本（万元） */
    @TableField("reg_capital")
    private BigDecimal regCapital;

    /** 实缴资本（万元） */
    @TableField("paid_in_capital")
    private BigDecimal paidInCapital;

    /** 公司类型（如: 有限责任公司） */
    @TableField("company_type")
    private String companyType;

    /** 注册登记日期 */
    @TableField("reg_date")
    private LocalDate regDate;

    /** 营业期限开始日期 */
    @TableField("business_term_start")
    private LocalDate businessTermStart;

    /** 营业期限截止日期 */
    @TableField("business_term_end")
    private LocalDate businessTermEnd;

    /** 是否长期（1:是, 0:否） */
    @TableField("is_long_term")
    private Integer isLongTerm;

    /** 身份证照片-头像面 */
    @TableField("id_card_front_url")
    private String idCardFrontUrl;

    /** 身份证照片-国徽面 */
    @TableField("id_card_back_url")
    private String idCardBackUrl;

    /** 主体登记证书（营业执照）图片路径 */
    @TableField("license_url")
    private String licenseUrl;

    /** 创建人ID */
    @TableField("created_by")
    private Long createdBy;

    /** 更新人ID */
    @TableField("updated_by")
    private Long updatedBy;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** 更新时间 */
    @TableField("updated_date")
    private LocalDateTime updatedDate;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableField("deleted")
    private Integer deleted;

    public static final String ID = "id";

    public static final String BUSINESS_NO = "business_no";

    public static final String VERSION_NO = "version_no";

    public static final String SUBJECT_ID = "subject_id";

    public static final String COMPANY_ID = "company_id";

    public static final String USCC = "uscc";

    public static final String SHORT_NAME = "short_name";

    public static final String EN_SHORT_NAME = "en_short_name";

    public static final String FORMER_NAME = "former_name";

    public static final String FORMER_SHORT_NAME = "former_short_name";

    public static final String EN_FORMER_NAME = "en_former_name";

    public static final String EN_FORMER_SHORT_NAME = "en_former_short_name";

    public static final String TAXPAYER_QUALIFICATION = "taxpayer_qualification";

    public static final String WEBSITE = "website";

    public static final String LEGAL_PERSON = "legal_person";

    public static final String ECONOMIC_NATURE = "economic_nature";

    public static final String INSURED_COUNT = "insured_count";

    public static final String REG_CAPITAL = "reg_capital";

    public static final String PAID_IN_CAPITAL = "paid_in_capital";

    public static final String COMPANY_TYPE = "company_type";

    public static final String REG_DATE = "reg_date";

    public static final String BUSINESS_TERM_START = "business_term_start";

    public static final String BUSINESS_TERM_END = "business_term_end";

    public static final String IS_LONG_TERM = "is_long_term";

    public static final String ID_CARD_FRONT_URL = "id_card_front_url";

    public static final String ID_CARD_BACK_URL = "id_card_back_url";

    public static final String LICENSE_URL = "license_url";

    public static final String CREATED_BY = "created_by";

    public static final String UPDATED_BY = "updated_by";

    public static final String CREATED_DATE = "created_date";

    public static final String UPDATED_DATE = "updated_date";

    public static final String DELETED = "deleted";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
