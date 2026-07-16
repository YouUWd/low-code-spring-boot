package com.jdec.platform.common_business.api.dto.request;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StudentReq implements Serializable {

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
