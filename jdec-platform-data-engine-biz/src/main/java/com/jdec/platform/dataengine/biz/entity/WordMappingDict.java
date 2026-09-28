package com.jdec.platform.dataengine.biz.entity;

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
 * @author
 * @since 2026-08-20
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("word_mapping_dict")
public class WordMappingDict extends Model<WordMappingDict> {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关系编号 */
    @TableField("business_no")
    private String businessNo;

    /** 词ID */
    @TableField("word_id")
    private Long wordId;

    /** 词编号 */
    @TableField("word_no")
    private String wordNo;

    /** 词名称 */
    @TableField("alias_value_title")
    private String aliasValueTitle;

    /** 背景色 */
    @TableField("bg_color")
    private String bgColor;

    /** 字体颜色 */
    @TableField("color")
    private String color;

    /** 边框颜色 */
    @TableField("border_color")
    private String borderColor;

    /** 图标 */
    @TableField("node_icon")
    private String nodeIcon;

    @TableField("path")
    private String path;

    /** 创建时间 */
    @TableField("create_date")
    private LocalDateTime createDate;

    /** 修改时间 */
    @TableField("update_date")
    private LocalDateTime updateDate;

    /** 创建人 */
    @TableField("create_by")
    private Long createBy;

    /** 修改人 */
    @TableField("update_by")
    private Long updateBy;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
