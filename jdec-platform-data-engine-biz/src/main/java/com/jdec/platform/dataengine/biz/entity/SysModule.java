package com.jdec.platform.dataengine.biz.entity;

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
 * 低代码业务模块定义表
 *
 * @author
 * @since 2026-09-14
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("sys_module")
public class SysModule extends Model<SysModule> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 项目编号 */
    @TableField("project_no")
    private String projectNo;

    /** 所属主体ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 模块唯一编码 (如 STUDENT-MODULE) */
    @TableField("module_code")
    private String moduleCode;

    /** 模块名称 */
    @TableField("module_name")
    private String moduleName;

    /** 模块描述 */
    @TableField("module_desc")
    private String moduleDesc;

    /** 物理主表名 */
    @TableField("primary_table")
    private String primaryTable;

    /** 父模块ID（0表示根模块） */
    @TableField("parent_id")
    private Long parentId;

    /** 同级排序 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 创建人ID */
    @TableField("created_by")
    private Long createdBy;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** 更新人ID */
    @TableField("updated_by")
    private Long updatedBy;

    /** 更新时间 */
    @TableField("updated_date")
    private LocalDateTime updatedDate;

    public static final String ID = "id";

    public static final String PROJECT_NO = "project_no";

    public static final String SUBJECT_ID = "subject_id";

    public static final String MODULE_CODE = "module_code";

    public static final String MODULE_NAME = "module_name";

    public static final String MODULE_DESC = "module_desc";

    public static final String PRIMARY_TABLE = "primary_table";

    public static final String PARENT_ID = "parent_id";

    public static final String SORT_ORDER = "sort_order";

    public static final String CREATED_BY = "created_by";

    public static final String CREATED_DATE = "created_date";

    public static final String UPDATED_BY = "updated_by";

    public static final String UPDATED_DATE = "updated_date";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
