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
 * 外部对象组织与人员树形表（Tab 2 人员构成）
 *
 * @author JDEC Team
 * @since 2026-10-19
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("member")
public class Member extends Model<Member> {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 人员业务唯一标识号 */
    @TableField("business_no")
    private String businessNo;

    /** 单据快照版本号 */
    @TableField("version_no")
    private Integer versionNo;

    /** 主体ID */
    @TableField("subject_id")
    private Long subjectId;

    /** 所属公司ID（关联 info.id） */
    @TableField("company_id")
    private Long companyId;

    /** 节点类型（DEPT:部门, EMPLOYEE:员工） */
    @TableField("type")
    private String type;

    /** 父节点ID（员工指向部门，子部门指向上级部门，顶级部门为NULL） */
    @TableField("parent_id")
    private Long parentId;

    /** 成员名称 */
    @TableField("member_name")
    private String memberName;

    /** 部门名称 或 员工姓名 */
    @TableField("name")
    private String name;

    /** 外部部门号（如: W12234）或工号 */
    @TableField("code")
    private String code;

    /** 部门负责人（0：否，1:是） */
    @TableField("dept_leader_flag")
    private Integer deptLeaderFlag;

    /** 职务/岗位（type=EMPLOYEE时有效） */
    @TableField("position")
    private String position;

    /** 展示排序序号 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 状态（1:在职/启用, 0:离职/停用） */
    @TableField("status")
    private String status;

    /** 创建人ID */
    @TableField("created_by")
    private Long createdBy;

    /** 更新人ID */
    @TableField("updated_by")
    private Long updatedBy;

    /** 创建时间 */
    @TableField("created_date")
    private LocalDateTime createdDate;

    /** 更新时间 */
    @TableField("updated_date")
    private LocalDateTime updatedDate;

    /** 逻辑删除: 0-未删除, 1-已删除 */
    @TableField("deleted")
    private Integer deleted;

    /** 节点ID */
    @TableField("node_id")
    private String nodeId;

    /** 父节点ID */
    @TableField("parent_node_id")
    private String parentNodeId;

    public static final String ID = "id";

    public static final String BUSINESS_NO = "business_no";

    public static final String VERSION_NO = "version_no";

    public static final String SUBJECT_ID = "subject_id";

    public static final String COMPANY_ID = "company_id";

    public static final String TYPE = "type";

    public static final String PARENT_ID = "parent_id";

    public static final String MEMBER_NAME = "member_name";

    public static final String NAME = "name";

    public static final String CODE = "code";

    public static final String DEPT_LEADER_FLAG = "dept_leader_flag";

    public static final String POSITION = "position";

    public static final String SORT_ORDER = "sort_order";

    public static final String STATUS = "status";

    public static final String CREATED_BY = "created_by";

    public static final String UPDATED_BY = "updated_by";

    public static final String CREATED_DATE = "created_date";

    public static final String UPDATED_DATE = "updated_date";

    public static final String DELETED = "deleted";

    public static final String NODE_ID = "node_id";

    public static final String PARENT_NODE_ID = "parent_node_id";

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
