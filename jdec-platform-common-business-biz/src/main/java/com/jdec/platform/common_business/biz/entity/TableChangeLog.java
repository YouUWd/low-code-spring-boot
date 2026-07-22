package com.jdec.platform.common_business.biz.entity;

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
 * @since 2026-07-21
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("table_change_log")
public class TableChangeLog extends Model<TableChangeLog> {

    private static final long serialVersionUID = 1L;

    /** 自增ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 数据ID */
    @TableField("data_id")
    private Long dataId;

    /** 用户的主题ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 模块ID */
    @TableField("module_id")
    private Long moduleId;

    /** 模块标识符 */
    @TableField("module_slug")
    private String moduleSlug;

    /** 模块名称 */
    @TableField("module_name")
    private String moduleName;

    /** 变更字段内容 */
    @TableField("change_content")
    private String changeContent;

    /** 模拟用户ID */
    @TableField("mock_user_id")
    private Long mockUserId;

    /** 模拟用户名称 */
    @TableField("mock_user_name")
    private String mockUserName;

    /** 模拟用户部门ID */
    @TableField("mock_department_id")
    private String mockDepartmentId;

    /** 模拟用户部门名称 */
    @TableField("mock_department_name")
    private String mockDepartmentName;

    /** 模拟用户岗位ID */
    @TableField("mock_job_id")
    private String mockJobId;

    /** 模拟用户岗位名称 */
    @TableField("mock_job_name")
    private String mockJobName;

    /** 登录用户ID */
    @TableField("real_user_id")
    private Long realUserId;

    /** 登录用户名称 */
    @TableField("real_user_name")
    private String realUserName;

    /** 真实用户部门ID */
    @TableField("real_department_id")
    private String realDepartmentId;

    /** 真实用户部门名称 */
    @TableField("real_department_name")
    private String realDepartmentName;

    /** 真实用户岗位ID */
    @TableField("real_job_id")
    private String realJobId;

    /** 真实用户岗位名称 */
    @TableField("real_job_name")
    private String realJobName;

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

    public static final String DATA_ID = "data_id";

    public static final String SUBJECT_ID = "subject_id";

    public static final String MODULE_ID = "module_id";

    public static final String MODULE_SLUG = "module_slug";

    public static final String MODULE_NAME = "module_name";

    public static final String CHANGE_CONTENT = "change_content";

    public static final String MOCK_USER_ID = "mock_user_id";

    public static final String MOCK_USER_NAME = "mock_user_name";

    public static final String MOCK_DEPARTMENT_ID = "mock_department_id";

    public static final String MOCK_DEPARTMENT_NAME = "mock_department_name";

    public static final String MOCK_JOB_ID = "mock_job_id";

    public static final String MOCK_JOB_NAME = "mock_job_name";

    public static final String REAL_USER_ID = "real_user_id";

    public static final String REAL_USER_NAME = "real_user_name";

    public static final String REAL_DEPARTMENT_ID = "real_department_id";

    public static final String REAL_DEPARTMENT_NAME = "real_department_name";

    public static final String REAL_JOB_ID = "real_job_id";

    public static final String REAL_JOB_NAME = "real_job_name";

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
