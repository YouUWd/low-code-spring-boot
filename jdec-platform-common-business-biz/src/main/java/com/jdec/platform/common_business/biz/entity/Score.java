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
 * 成绩表
 *
 * @author
 * @since 2026-06-03
 */
@Getter
@Setter
@ToString
@TableName("score")
@Accessors(chain = true)
public class Score extends Model<Score> {

    private static final long serialVersionUID = 1L;

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

    /** 创建时间 */
    @TableField("create_date")
    private LocalDateTime createDate;

    /** 更新时间 */
    @TableField("update_date")
    private LocalDateTime updateDate;

    /** 主体ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 主体名称 */
    @TableField("subject_name")
    private String subjectName;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
