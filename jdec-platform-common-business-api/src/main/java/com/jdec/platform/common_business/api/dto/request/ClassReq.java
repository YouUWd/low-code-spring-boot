package com.jdec.platform.common_business.api.dto.request;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;

@Data
public class ClassReq implements Serializable {

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

    @TableField("subject_id")
    private Long subjectId;

    //    /** 创建时间 */
    //    @TableField("create_date")
    //    private LocalDateTime createDate;
    //
    //    /** 修改时间 */
    //    @TableField("update_date")
    //    private LocalDateTime updateDate;
}
