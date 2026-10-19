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
 * 外部对象地址信息表（Tab 3 地址部分）
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("address")
public class Address extends Model<Address> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务单据编号 */
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

    /** 通讯地址-国家 */
    @TableField("comm_country")
    private String commCountry;

    /** 通讯地址-省/市/区 */
    @TableField("comm_region")
    private String commRegion;

    /** 通讯地址-详细地址 */
    @TableField("comm_detail")
    private String commDetail;

    /** 通讯地址-邮政编码 */
    @TableField("comm_postcode")
    private String commPostcode;

    /** 注册地址-国家 */
    @TableField("reg_country")
    private String regCountry;

    /** 注册地址-省/市/区 */
    @TableField("reg_region")
    private String regRegion;

    /** 注册地址-详细地址 */
    @TableField("reg_detail")
    private String regDetail;

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

    public static final String COMM_COUNTRY = "comm_country";

    public static final String COMM_REGION = "comm_region";

    public static final String COMM_DETAIL = "comm_detail";

    public static final String COMM_POSTCODE = "comm_postcode";

    public static final String REG_COUNTRY = "reg_country";

    public static final String REG_REGION = "reg_region";

    public static final String REG_DETAIL = "reg_detail";

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
