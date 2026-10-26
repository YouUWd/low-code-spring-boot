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
 * 人员联系方式表（兼具个人通讯录与公司对外联络窗口职责）
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("member_contact")
public class MemberContact extends Model<MemberContact> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 人员联系方式业务编号 */
    @TableField("business_no")
    private String businessNo;

    /** 快照版本号 */
    @TableField("version_no")
    private Integer versionNo;

    /** 主体ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 所属员工ID（关联 member.id，且必须为 type=EMPLOYEE） */
    @TableField("member_id")
    private Long memberId;

    /** 联系方式类型（手机/座机/工作邮箱等） */
    @TableField("contact_type")
    private String contactType;

    /** 具体电话号码或邮箱地址 */
    @TableField("contact_value")
    private String contactValue;

    /** 是否为员工本人的默认联系方式（1:是, 0:否） */
    @TableField("is_primary")
    private Integer isPrimary;

    /** 排序序号（用于 Tab 3 表格序号 1, 2...） */
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

    public static final String MEMBER_ID = "member_id";

    public static final String CONTACT_TYPE = "contact_type";

    public static final String CONTACT_VALUE = "contact_value";

    public static final String IS_PRIMARY = "is_primary";

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
