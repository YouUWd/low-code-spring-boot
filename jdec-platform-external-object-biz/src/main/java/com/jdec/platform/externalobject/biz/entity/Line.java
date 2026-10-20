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
 * 产线表
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("line")
public class Line extends Model<Line> {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 场地id */
    @TableField("venue_id")
    private Long venueId;

    /** 产线名称 */
    @TableField("name")
    private String name;

    /** 产线负责人 */
    @TableField("person")
    private String person;

    /** 联系电话 */
    @TableField("phone")
    private String phone;

    /** 产线描述 */
    @TableField("description")
    private String description;

    /** 主体id */
    @TableField("subject_id")
    private Long subjectId;

    /** 业务编码 */
    @TableField("business_no")
    private String businessNo;

    /** 业务状态 */
    @TableField("business_status")
    private Integer businessStatus;

    /** 版本号 */
    @TableField("version_no")
    private Integer versionNo;

    /** 模块id */
    @TableField("module_id")
    private Long moduleId;

    /** 创建人id */
    @TableField("created_by")
    private Long createdBy;

    /** 更新人id */
    @TableField("updated_by")
    private Long updatedBy;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** 更新时间 */
    @TableField("updated_date")
    private LocalDateTime updatedDate;

    /** 逻辑删除 */
    @TableField("deleted")
    private Integer deleted;

    public static final String ID = "id";

    public static final String VENUE_ID = "venue_id";

    public static final String NAME = "name";

    public static final String PERSON = "person";

    public static final String PHONE = "phone";

    public static final String DESCRIPTION = "description";

    public static final String SUBJECT_ID = "subject_id";

    public static final String BUSINESS_NO = "business_no";

    public static final String BUSINESS_STATUS = "business_status";

    public static final String VERSION_NO = "version_no";

    public static final String MODULE_ID = "module_id";

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
