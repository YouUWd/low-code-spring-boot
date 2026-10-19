package com.jdec.platform.externalobject.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 操作日志索引表
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("monitor_operate_index")
public class MonitorOperateIndex extends Model<MonitorOperateIndex> {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 日志id */
    @TableField("log_id")
    private Long logId;

    /** 模块id */
    @TableField("module_id")
    private Long moduleId;

    /** 单据id */
    @TableField("data_id")
    private Long dataId;

    public static final String ID = "id";

    public static final String LOG_ID = "log_id";

    public static final String MODULE_ID = "module_id";

    public static final String DATA_ID = "data_id";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
