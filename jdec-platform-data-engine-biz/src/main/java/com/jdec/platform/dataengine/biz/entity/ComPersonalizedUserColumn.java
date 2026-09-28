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
 * @author
 * @since 2026-07-15
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("com_personalized_user_column")
public class ComPersonalizedUserColumn extends Model<ComPersonalizedUserColumn> {

    private static final long serialVersionUID = 1L;

    /** 自增ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 系统编码 */
    @TableField("project_no")
    private String projectNo;

    /** 模块ID */
    @TableField("module_id")
    private Long moduleId;

    /** 模块列配置 */
    @TableField("column_property")
    private String columnProperty;

    /** 操作用户 */
    @TableField("created_by")
    private Long createdBy;

    /** 操作用户名称 */
    @TableField("created_name")
    private String createdName;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** 更改人 */
    @TableField("updated_by")
    private Long updatedBy;

    /** 更改人名称 */
    @TableField("updated_name")
    private String updatedName;

    /** 更改时间 */
    @TableField("updated_date")
    private LocalDateTime updatedDate;

    /** 1删除0正常 */
    @TableField("deleted")
    private Boolean deleted;

    public static final String ID = "id";

    public static final String USER_ID = "user_id";

    public static final String PROJECT_NO = "project_no";

    public static final String MODULE_ID = "module_id";

    public static final String COLUMN_PROPERTY = "column_property";

    public static final String CREATED_BY = "created_by";

    public static final String CREATED_NAME = "created_name";

    public static final String CREATED_DATE = "created_date";

    public static final String UPDATED_BY = "updated_by";

    public static final String UPDATED_NAME = "updated_name";

    public static final String UPDATED_DATE = "updated_date";

    public static final String DELETED = "deleted";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
