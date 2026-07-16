package com.jdec.platform.common_business.biz.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * @author
 * @since 2026-06-05
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("student_specialty")
public class StudentSpecialty extends Model<StudentSpecialty> {

    private static final long serialVersionUID = 1L;

    @TableId("id")
    private Long id;

    @TableField("student_id")
    private Long studentId;

    @TableField("specialty_name")
    private String specialtyName;

    @TableField("LEVEL")
    private String level;

    @Override
    public Serializable pkVal() {
        return null;
    }
}
