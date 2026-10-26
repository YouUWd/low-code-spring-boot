package com.jdec.platform.externalobject.biz.entity;

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
 * 用户自定义表头配置
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("user_menu_header")
public class UserMenuHeader extends Model<UserMenuHeader> {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户id */
    @TableField("user_id")
    private Long userId;

    /** 虚拟id(类型-id) */
    @TableField("vir_id")
    private String virId;

    /** 所属列表菜单id */
    @TableField("menu_id")
    private Long menuId;

    /** 父节点ID(0=顶层)，可为MENU也可为FIELD之下挂子级 */
    @TableField("parent_id")
    private String parentId;

    /** 节点类型: 0-菜单 / 1-字段 */
    @TableField("node_type")
    private Integer nodeType;

    /** 来源ID */
    @TableField("source_id")
    private Long sourceId;

    /** 模块id */
    @TableField("module_id")
    private Long moduleId;

    /** 显示名覆盖。FIELD空=取字段显示名，MENU空=取来源菜单名 */
    @TableField("label")
    private String label;

    /** 菜单显示颜色 */
    @TableField("colors")
    private String colors;

    /** 列宽(px) */
    @TableField("width")
    private Integer width;

    /** 字段索引 */
    @TableField("data_index")
    private String dataIndex;

    /** 可排序 */
    @TableField("sorted")
    private Integer sorted;

    /** 搜索控件类型(区间/单选/模糊等) */
    @TableField("search_type")
    private String searchType;

    /** 文本是否省略 */
    @TableField("ellipsis")
    private Integer ellipsis;

    /** 固定(left-左固定，right-右固定，""-不固定) */
    @TableField("fixed")
    private String fixed;

    /** 是否选中状态 */
    @TableField("checked")
    private Integer checked;

    /** 排序值 */
    @TableField("sort_value")
    private Integer sortValue;

    /** 所属主体 */
    @TableField("subject_id")
    private Long subjectId;

    /** 应用编码 */
    @TableField("project_no")
    private String projectNo;

    /** created_by */
    @TableField("created_by")
    private Long createdBy;

    /** created_name */
    @TableField("created_name")
    private String createdName;

    /** created_date */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** updated_by */
    @TableField("updated_by")
    private Long updatedBy;

    /** updated_name */
    @TableField("updated_name")
    private String updatedName;

    /** updated_date */
    @TableField("updated_date")
    private LocalDateTime updatedDate;

    public static final String ID = "id";

    public static final String USER_ID = "user_id";

    public static final String VIR_ID = "vir_id";

    public static final String MENU_ID = "menu_id";

    public static final String PARENT_ID = "parent_id";

    public static final String NODE_TYPE = "node_type";

    public static final String SOURCE_ID = "source_id";

    public static final String MODULE_ID = "module_id";

    public static final String LABEL = "label";

    public static final String COLORS = "colors";

    public static final String WIDTH = "width";

    public static final String DATA_INDEX = "data_index";

    public static final String SORTED = "sorted";

    public static final String SEARCH_TYPE = "search_type";

    public static final String ELLIPSIS = "ellipsis";

    public static final String FIXED = "fixed";

    public static final String CHECKED = "checked";

    public static final String SORT_VALUE = "sort_value";

    public static final String SUBJECT_ID = "subject_id";

    public static final String PROJECT_NO = "project_no";

    public static final String CREATED_BY = "created_by";

    public static final String CREATED_NAME = "created_name";

    public static final String CREATED_DATE = "created_date";

    public static final String UPDATED_BY = "updated_by";

    public static final String UPDATED_NAME = "updated_name";

    public static final String UPDATED_DATE = "updated_date";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
