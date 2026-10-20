package com.jdec.platform.externalobject.biz.validation;

/**
 * 外部对象表单涉及的模块 ID 与页签名称。
 *
 * <p>模块 ID 与 {@code config_center.sys_module}、{@code sys_menu} 中的配置保持一致。
 */
public final class ExternalObjectModules {

    /** 外部对象主档（info） */
    public static final String INFO = "1001";

    /** 基本信息（basic_info） */
    public static final String BASIC_INFO = "1002";

    /** 人员构成-组织架构（member） */
    public static final String MEMBER = "1003";

    /** 人员构成-个人通讯录（member_contact） */
    public static final String MEMBER_CONTACT = "1004";

    /** 联系方式-公司地址（address） */
    public static final String ADDRESS = "1005";

    /** 联系方式-公司联系方式（company_contact） */
    public static final String COMPANY_CONTACT = "1006";

    /** 财务信息（结构模块，无独立表） */
    public static final String FINANCE = "1007";

    /** 银行卡（financial_account） */
    public static final String FINANCIAL_ACCOUNT = "1008";

    /** 电子账户（digital_account） */
    public static final String DIGITAL_ACCOUNT = "1009";

    /** 信用等级（credit_rating） */
    public static final String CREDIT_RATING = "1010";

    /** 联系方式（结构模块，无独立表） */
    public static final String CONTACT_GROUP = "1020";

    /** 场地产线管理（venue_line） */
    public static final String VENUE_LINE = "1021";

    /** 场地信息（venue） */
    public static final String VENUE = "1032";

    /** 产线信息（line） */
    public static final String LINE = "1033";

    public static final String TAB_BASIC_INFO = "基本信息";
    public static final String TAB_MEMBER = "人员构成";
    public static final String TAB_FINANCE = "财务信息";
    public static final String TAB_CONTACT = "联系方式";
    public static final String TAB_CREDIT_RATING = "信用等级";
    public static final String TAB_VENUE = "场地";
    public static final String TAB_LINE = "产线";

    private ExternalObjectModules() {}
}
