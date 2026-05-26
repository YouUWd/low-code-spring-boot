package com.jdec.platform.config.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** 审批链配置详情响应 */
@Data
@Schema(description = "审批链配置详情响应")
public class ApprovalChainConfigDetailResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "所属模块ID")
    private Long moduleId;

    @Schema(description = "上一步步骤")
    private Integer upStep;

    @Schema(description = "当前步骤")
    private Integer currentStep;

    @Schema(description = "下一步步骤")
    private Integer nextStep;

    @Schema(description = "审批链分类ID")
    private Long approvalChainTypeId;

    @Schema(description = "审批规则")
    private String approvalRule;

    @Schema(description = "角色审批百分比 0~100")
    private Integer roleApprovalPercent;

    @Schema(description = "审批角色ID")
    private Long approveRoleId;

    @Schema(description = "审批人ID")
    private Long approverId;

    @Schema(description = "移交人ID")
    private Long delegateApproverId;

    @Schema(description = "是否跳过步骤 1:跳过 0:不跳过")
    private Integer skipped;

    @Schema(description = "1:显示，0:隐藏")
    private Integer showed;

    @Schema(description = "子模块集合ID, 英文逗号分隔")
    private String childModuleIds;

    @Schema(description = "当前状态ID")
    private Long currentStatusId;

    @Schema(description = "当前状态链路（从根节点到当前节点）")
    private List<StatusNode> currentStatusChain;

    @Schema(description = "下一状态ID")
    private Long nextStatusId;

    @Schema(description = "下一状态链路（从根节点到当前节点）")
    private List<StatusNode> nextStatusChain;

    @Schema(description = "1:自动审批，0:不能自动审批")
    private Integer autoApproved;

    @Schema(description = "系统自动办理时间")
    private Integer autoApprovedTime;

    @Schema(description = "办理人未办理的最迟时间")
    private Integer deadlineTime;

    @Schema(description = "按钮ID列表")
    private String buttonList;

    @Schema(description = "是否协同办理 0:否 1:是")
    private Integer collaborated;

    @Schema(description = "是否可以重复提交 0:否 1:是")
    private Integer repeated;

    @Schema(description = "消息模板ID集合, 英文逗号分隔")
    private String msgTemplateIds;

    @Schema(description = "是否默认，1：是，0：否")
    private Integer defaultFlag;

    @Schema(description = "按钮配置列表")
    private List<ButtonConfigDetail> buttonConfigs;

    /** 状态节点 */
    @Data
    @Schema(description = "状态节点")
    public static class StatusNode {

        @Schema(description = "状态ID")
        private Long id;

        @Schema(description = "父状态ID")
        private Long pid;

        @Schema(description = "状态名称")
        private String title;

        @Schema(description = "状态值")
        private Integer statusValue;

        @Schema(description = "背景色")
        private String statusBackground;

        @Schema(description = "字体色")
        private String statusFontColor;
    }

    /** 按钮配置详情 */
    @Data
    @Schema(description = "按钮配置详情")
    public static class ButtonConfigDetail {

        @Schema(description = "按钮配置ID")
        private Long id;

        @Schema(description = "操作类型ID")
        private Long buttonTypeId;

        @Schema(description = "按钮ID")
        private Long buttonId;

        @Schema(description = "日志对应图标")
        private String icon;

        @Schema(description = "日志对应状态描述")
        private String logName;

        @Schema(description = "消息模板ID集合")
        private String msgTemplateIds;
    }
}
