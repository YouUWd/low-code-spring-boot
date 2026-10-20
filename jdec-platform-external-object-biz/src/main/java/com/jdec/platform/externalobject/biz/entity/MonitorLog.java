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
 * 监控日志
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("monitor_log")
public class MonitorLog extends Model<MonitorLog> {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 日志类型;0-操作日志，1-消息日志，2-系统日志 */
    @TableField("type")
    private Integer type;

    /** 系统日志类型 */
    @TableField("sys_type")
    private String sysType;

    /** 主体id */
    @TableField("subject_id")
    private Long subjectId;

    /** 主体名称 */
    @TableField("subject_name")
    private String subjectName;

    /** 审批数据 */
    @TableField("approve")
    private String approve;

    /** 登录信息; */
    @TableField("login_info")
    private String loginInfo;

    /** 模拟信息; */
    @TableField("mock_info")
    private String mockInfo;

    /** 请求IP */
    @TableField("ip")
    private String ip;

    /** 模块id */
    @TableField("module_id")
    private Long moduleId;

    /** 单据id */
    @TableField("data_id")
    private Long dataId;

    /** 浏览器信息 */
    @TableField("browser")
    private String browser;

    /** 平台信息 */
    @TableField("plat_form")
    private String platForm;

    /** 日志内容; */
    @TableField("content")
    private String content;

    /** 备注; */
    @TableField("remark")
    private String remark;

    /** 日志记录时间 */
    @TableField("record_time")
    private LocalDateTime recordTime;

    /** 日志保存时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    public static final String ID = "id";

    public static final String TYPE = "type";

    public static final String SYS_TYPE = "sys_type";

    public static final String SUBJECT_ID = "subject_id";

    public static final String SUBJECT_NAME = "subject_name";

    public static final String APPROVE = "approve";

    public static final String LOGIN_INFO = "login_info";

    public static final String MOCK_INFO = "mock_info";

    public static final String IP = "ip";

    public static final String MODULE_ID = "module_id";

    public static final String DATA_ID = "data_id";

    public static final String BROWSER = "browser";

    public static final String PLAT_FORM = "plat_form";

    public static final String CONTENT = "content";

    public static final String REMARK = "remark";

    public static final String RECORD_TIME = "record_time";

    public static final String CREATED_DATE = "created_date";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
