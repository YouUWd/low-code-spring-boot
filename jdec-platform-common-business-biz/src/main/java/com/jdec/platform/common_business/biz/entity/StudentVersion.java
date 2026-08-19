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
 * 学生单据记录表
 *
 * @author
 * @since 2026-08-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("student_version")
public class StudentVersion extends Model<StudentVersion> {

    private static final long serialVersionUID = 1L;

    /** 学生ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 学号 */
    @TableField("student_no")
    private String studentNo;

    /** 班级ID */
    @TableField("class_id")
    private Long classId;

    /** 学生姓名 */
    @TableField("student_name")
    private String studentName;

    /** 学生性别 */
    @TableField("gender")
    private Integer gender;

    /** 出生日期 */
    @TableField("birth_date")
    private LocalDateTime birthDate;

    /** 入学日期 */
    @TableField("enrollment_date")
    private LocalDateTime enrollmentDate;

    /** 状态(1.在读;2.休学) */
    @TableField("student_status")
    private Integer studentStatus;

    /** 创建时间 */
    @TableField("create_date")
    private LocalDateTime createDate;

    /** 修改时间 */
    @TableField("update_date")
    private LocalDateTime updateDate;

    /** 主体ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 主体名称 */
    @TableField("subject_name")
    private String subjectName;

    /** 创建人 */
    @TableField("create_by")
    private Long createBy;

    /** 修改人 */
    @TableField("update_by")
    private Long updateBy;

    /** 业务状态 */
    @TableField("business_status")
    private Integer businessStatus;

    /** 是否删除 */
    @TableField("deleted")
    private Boolean deleted;

    /** 模块ID */
    @TableField("module_id")
    private Long moduleId;

    /** 来源ID，快照数据保存第一次创建那条数据的ID。 */
    @TableField("source_id")
    private Long sourceId;

    /** 版本号 */
    @TableField("version_no")
    private Integer versionNo;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
