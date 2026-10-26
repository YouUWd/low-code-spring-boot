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
 * 低代码模块物理字段字典表
 *
 * @author
 * @since 2026-09-14
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("sys_module_field")
public class SysModuleField extends Model<SysModuleField> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属模块ID */
    @TableField("module_id")
    private Long moduleId;

    /** 物理表名 */
    @TableField("table_name")
    private String tableName;

    /** 物理列名 */
    @TableField("column_name")
    private String columnName;

    /** 前端展示标签 */
    @TableField("display_name")
    private String displayName;

    /** 显示排序 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 状态: 1-启用, 0-停用 */
    @TableField("status")
    private Integer status;

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

    public static final String MODULE_ID = "module_id";

    public static final String TABLE_NAME = "table_name";

    public static final String COLUMN_NAME = "column_name";

    public static final String DISPLAY_NAME = "display_name";

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
