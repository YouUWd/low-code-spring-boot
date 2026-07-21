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
 * 班级单据记录表
 *
 * @author
 * @since 2026-07-21
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("class_version")
public class ClassVersion extends Model<ClassVersion> {

    private static final long serialVersionUID = 1L;

    /** 班级ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 班级编码 */
    @TableField("class_no")
    private String classNo;

    /** 班级名称 */
    @TableField("class_name")
    private String className;

    /** 年级 */
    @TableField("grade")
    private Integer grade;

    /** 班主任名称 */
    @TableField("class_teacher_name")
    private String classTeacherName;

    /** 学生人数 */
    @TableField("student_count")
    private Integer studentCount;

    /** 教室容量 */
    @TableField("classroom_capacity")
    private Integer classroomCapacity;

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

    /** 是否删除 */
    @TableField("deleted")
    private Boolean deleted;

    /** 模块ID */
    @TableField("module_id")
    private Long moduleId;

    public static final String ID = "id";

    public static final String CLASS_NO = "class_no";

    public static final String CLASS_NAME = "class_name";

    public static final String GRADE = "grade";

    public static final String CLASS_TEACHER_NAME = "class_teacher_name";

    public static final String STUDENT_COUNT = "student_count";

    public static final String CLASSROOM_CAPACITY = "classroom_capacity";

    public static final String CREATE_DATE = "create_date";

    public static final String UPDATE_DATE = "update_date";

    public static final String SUBJECT_ID = "subject_id";

    public static final String SUBJECT_NAME = "subject_name";

    public static final String DELETED = "deleted";

    public static final String MODULE_ID = "module_id";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
