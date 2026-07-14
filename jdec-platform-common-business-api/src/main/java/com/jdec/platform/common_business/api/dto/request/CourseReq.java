package com.jdec.platform.common_business.api.dto.request;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class CourseReq {

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
