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
 * 公司联系方式表（对外联络窗口与公开渠道明细）
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("company_contact")
public class CompanyContact extends Model<CompanyContact> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务编号 */
    @TableField("business_no")
    private String businessNo;

    /** 快照版本号 */
    @TableField("version_no")
    private Integer versionNo;

    /** 主体ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 所属公司ID（关联 info.id） */
    @TableField("company_id")
    private Long companyId;

    /** 公司联系人 */
    @TableField("contact_name")
    private String contactName;

    /** 联系方式类型（电话/座机/邮箱/传真等） */
    @TableField("contact_type")
    private String contactType;

    /** 具体联系号码或邮箱地址 */
    @TableField("contact_value")
    private String contactValue;

    /** 排序序号（用于前端表格行序号） */
    @TableField("sort_order")
    private Integer sortOrder;

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

    public static final String CONTACT_NAME = "contact_name";

    public static final String CONTACT_TYPE = "contact_type";

    public static final String CONTACT_VALUE = "contact_value";

    public static final String SORT_ORDER = "sort_order";

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
