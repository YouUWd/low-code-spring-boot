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
 * 课程表
 *
 * @author
 * @since 2026-06-03
 */
@Getter
@Setter
@ToString
@TableName("course")
@Accessors(chain = true)
public class Course extends Model<Course> {

    private static final long serialVersionUID = 1L;

    /** 课程ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 课程编码 */
    @TableField("course_no")
    private String courseNo;

    /** 课程名称 */
    @TableField("course_name")
    private String courseName;

    /** 课程学分 */
    @TableField("course_credit")
    private Double courseCredit;

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

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
