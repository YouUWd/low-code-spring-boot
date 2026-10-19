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
 * 外部对象银行卡结算账户表（Tab 4-1）
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("financial_account")
public class FinancialAccount extends Model<FinancialAccount> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务单号/统一业务编号 */
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

    /** 行别（如: 中国建设银行） */
    @TableField("bank_head")
    private String bankHead;

    /** 开户行（支行全称） */
    @TableField("bank_branch")
    private String bankBranch;

    /** 账户名（非政府机关时前端置灰只读，并默认带出外部对象全称） */
    @TableField("account_name")
    private String accountName;

    /** 账号/银行卡号 */
    @TableField("account_no")
    private String accountNo;

    /** 账号状态（NORMAL:正常, FROZEN:冻结, CANCELLED:注销） */
    @TableField("account_status")
    private String accountStatus;

    /** 前端表格排序序号 */
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

    public static final String BANK_HEAD = "bank_head";

    public static final String BANK_BRANCH = "bank_branch";

    public static final String ACCOUNT_NAME = "account_name";

    public static final String ACCOUNT_NO = "account_no";

    public static final String ACCOUNT_STATUS = "account_status";

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
