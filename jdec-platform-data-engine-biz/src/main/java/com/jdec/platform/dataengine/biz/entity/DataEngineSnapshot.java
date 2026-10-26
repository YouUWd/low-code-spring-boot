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
 * 数据引擎-业务聚合多版本快照表
 *
 * @author
 * @since 2026-08-26
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("data_engine_snapshot")
public class DataEngineSnapshot extends Model<DataEngineSnapshot> {

    private static final long serialVersionUID = 1L;

    /** 快照主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属 SysModule ID */
    @TableField("module_id")
    private Long moduleId;

    /** 业务主表主键 ID */
    @TableField("data_id")
    private Long dataId;

    /** 版本号 */
    @TableField("version_no")
    private Integer versionNo;

    /** 业务单据编号 */
    @TableField("business_no")
    private String businessNo;

    /** 终结状态值: 99-生效, -99-作废 */
    @TableField("final_status_value")
    private Integer finalStatusValue;

    /** 主子表完整 JSON 快照 */
    @TableField("json_data")
    private String jsonData;

    /** 字段级变更差异 JSON */
    @TableField("change_diff")
    private String changeDiff;

    /** 归档备注 */
    @TableField("remark")
    private String remark;

    /** 操作人ID */
    @TableField("created_by")
    private Long createdBy;

    /** 操作人姓名 */
    @TableField("created_name")
    private String createdName;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
