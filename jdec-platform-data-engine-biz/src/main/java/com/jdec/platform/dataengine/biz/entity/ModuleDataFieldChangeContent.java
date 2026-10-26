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
 * 模块数据变更记录
 *
 * @author
 * @since 2026-09-28
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("module_data_field_change_content")
public class ModuleDataFieldChangeContent extends Model<ModuleDataFieldChangeContent> {

    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作ID */
    @TableField("action_id")
    private Long actionId;

    /** 根模块ID */
    @TableField("root_module_id")
    private Long rootModuleId;

    /** 主表的数据ID */
    @TableField("root_table_data_id")
    private Long rootTableDataId;

    /** 模块主数据版本 */
    @TableField("root_table_data_version")
    private Integer rootTableDataVersion;

    /** 根模块ID */
    @TableField("module_id")
    private Long moduleId;

    /** 表名 */
    @TableField("table_name")
    private String tableName;

    /** 表数据ID */
    @TableField("data_id")
    private Long dataId;

    /** 列名 */
    @TableField("field_id")
    private String fieldId;

    /** 字段名 */
    @TableField("content")
    private String content;

    /** 快照1 */
    @TableField("first_snapshot")
    private String firstSnapshot;

    /** 快照2 */
    @TableField("second_snapshot")
    private String secondSnapshot;

    /** 1正常的业务2审批链业务 */
    @TableField("approval_flag")
    private Integer approvalFlag;

    /** 1插入操作2修改操作 */
    @TableField("action_type")
    private Integer actionType;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    public static final String ID = "id";

    public static final String ACTION_ID = "action_id";

    public static final String ROOT_MODULE_ID = "root_module_id";

    public static final String ROOT_TABLE_DATA_ID = "root_table_data_id";

    public static final String ROOT_TABLE_DATA_VERSION = "root_table_data_version";

    public static final String MODULE_ID = "module_id";

    public static final String TABLE_NAME = "table_name";

    public static final String DATA_ID = "data_id";

    public static final String FIELD_ID = "field_id";

    public static final String CONTENT = "content";

    public static final String FIRST_SNAPSHOT = "first_snapshot";

    public static final String SECOND_SNAPSHOT = "second_snapshot";

    public static final String APPROVAL_FLAG = "approval_flag";

    public static final String ACTION_TYPE = "action_type";

    public static final String CREATED_DATE = "created_date";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
