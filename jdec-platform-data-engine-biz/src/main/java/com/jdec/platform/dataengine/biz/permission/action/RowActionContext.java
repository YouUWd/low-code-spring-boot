package com.jdec.platform.dataengine.biz.permission.action;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单据行级互动动作决策上下文 (封装单据当前态与审批链上下文，供各 RowActionStrategy 策略判定) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RowActionContext {

    /** 所属模块 ID */
    private Long moduleId;

    /** 业务单据主键 ID (聚合根 ID) */
    private Long dataId;

    /** 当前登录用户 ID */
    private Long currentUserId;

    /** 当前登录用户拥有的角色 ID 列表 */
    private List<Long> currentUserRoleIds;

    /** 单据当前审批状态: 0-草稿, 1-审批中, 99-审批通过生效, -99-驳回/作废终止 */
    private Integer approvalStatus;

    /** 单据创建人/提单人 ID */
    private Long createdBy;

    /** 审批链当前节点 ID */
    private Long currentNodeId;

    /** 当前审批节点的审批人用户 ID 列表 */
    private List<Long> currentApproverUserIds;

    /** 当前审批节点的审批角色 ID 列表 */
    private List<Long> currentApproverRoleIds;

    /** 单据行原始物理数据字典 */
    private Map<String, Object> rawRecord;
}
