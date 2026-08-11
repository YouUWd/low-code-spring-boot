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
 * 班级表
 *
 * @author
 * @since 2026-06-03
 */
@Getter
@Setter
@ToString
@TableName("class")
@Accessors(chain = true)
public class ClassEntity extends Model<ClassEntity> {

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

    /** 班主任ID */
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

    /** 创建人 * */
    @TableField("create_by")
    private Long createBy;

    /** 修改人 * */
    @TableField("update_by")
    private Long updateBy;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
