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
 * 模块快照数据
 *
 * @author
 * @since 2026-09-28
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("module_data_tmp_snapshot_content")
public class ModuleDataTmpSnapshotContent extends Model<ModuleDataTmpSnapshotContent> {

    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 根模块ID */
    @TableField("root_module_id")
    private Long rootModuleId;

    /** 主表的数据ID */
    @TableField("root_table_data_id")
    private Long rootTableDataId;

    /** 模块主数据版本 */
    @TableField("root_table_data_version")
    private Integer rootTableDataVersion;

    /** 快照数据的状态 */
    @TableField("business_status")
    private Integer businessStatus;

    /** 字段名 */
    @TableField("content")
    private String content;

    /** 字段名 */
    @TableField("comparison")
    private String comparison;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    public static final String ID = "id";

    public static final String ROOT_MODULE_ID = "root_module_id";

    public static final String ROOT_TABLE_DATA_ID = "root_table_data_id";

    public static final String ROOT_TABLE_DATA_VERSION = "root_table_data_version";

    public static final String BUSINESS_STATUS = "business_status";

    public static final String CONTENT = "content";

    public static final String COMPARISON = "comparison";

    public static final String CREATED_DATE = "created_date";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
