package com.jdec.platform.common_business.api.dto.request;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ScoreReq implements Serializable {

    /** 分数ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    @TableField("student_id")
    private Long studentId;

    /** 课程ID */
    @TableField("course_id")
    private Long courseId;

    /** 学期 */
    @TableField("semester")
    private String semester;

    /** 分数 */
    @TableField("score")
    private Double score;

    /** 等级 */
    @TableField("grade_level")
    private String gradeLevel;

    /** 考试时间 */
    @TableField("exam_date")
    private LocalDateTime examDate;

    @TableField("subject_id")
    private Long subjectId;

    //    /** 创建时间 */
    //    @TableField("create_date")
    //    private LocalDateTime createDate;
    //
    //    /** 更新时间 */
    //    @TableField("update_date")
    //    private LocalDateTime updateDate;
}
