package com.jdec.platform.externalobject.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 外部对象主档表（顶部公共头核心信息）
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("info")
public class Info extends Model<Info> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务编号 */
    @TableField("business_no")
    private String businessNo;

    /** 版本号 */
    @TableField("version_no")
    private Integer versionNo;

    /** 活跃标识:0活跃1活跃 */
    @TableField("active")
    private Integer active;

    /** 主体ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 外部对象全称 */
    @TableField("company_name")
    private String companyName;

    /** 外部对象父级全称对应ID（自关联母集团，空为顶级） */
    @TableField("parent_id")
    private Long parentId;

    /** 对象性质（企业/个体工商户/社会团体/政府机关等） */
    @TableField("nature")
    private String nature;

    /** 登记状态（存续/在业/注销/吊销） */
    @TableField("reg_status")
    private String regStatus;

    /** 合作类型，JSON数组，如: ["客户", "供应商"] */
    @TableField("cooperation_types")
    private String cooperationTypes;

    /** 行业标签，JSON数组，如: ["兰炭", "焦化"] */
    @TableField("industry_tags")
    private String industryTags;

    /** 行业类型，JSON数组，如: ["纺织业", "食品制造业"] */
    @TableField("industry_types")
    private String industryTypes;

    /** 企业LOGO图片地址 */
    @TableField("logo_url")
    private String logoUrl;

    /** 性别，对象性质为个人的时候填写 */
    @TableField("gender")
    private String gender;

    /** 业务状态 */
    @TableField("business_status")
    private Integer businessStatus;

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

    /** 上一个版本的ID */
    @TableField("source_id")
    private Long sourceId;

    public static final String ID = "id";

    public static final String BUSINESS_NO = "business_no";

    public static final String VERSION_NO = "version_no";

    public static final String ACTIVE = "active";

    public static final String SUBJECT_ID = "subject_id";

    public static final String COMPANY_NAME = "company_name";

    public static final String PARENT_ID = "parent_id";

    public static final String NATURE = "nature";

    public static final String REG_STATUS = "reg_status";

    public static final String COOPERATION_TYPES = "cooperation_types";

    public static final String INDUSTRY_TAGS = "industry_tags";

    public static final String INDUSTRY_TYPES = "industry_types";

    public static final String LOGO_URL = "logo_url";

    public static final String GENDER = "gender";

    public static final String BUSINESS_STATUS = "business_status";

    public static final String CREATED_BY = "created_by";

    public static final String UPDATED_BY = "updated_by";

    public static final String CREATED_DATE = "created_date";

    public static final String UPDATED_DATE = "updated_date";

    public static final String DELETED = "deleted";

    public static final String SOURCE_ID = "source_id";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
