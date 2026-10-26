package com.jdec.platform.config.api.dto.request;

import com.jdec.platform.config.api.dto.response.ButtonStyleDTO;
import com.jdec.platform.shared.audit.annotation.AuditField;
import com.jdec.platform.shared.audit.enums.FieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/** 审批链配置保存请求 */
@Data
@Schema(description = "审批链配置保存请求")
public class ApprovalChainConfigSaveReq {

    @Schema(description = "主键ID（编辑时必填，新增时为空）")
    private Long id;

    @Schema(description = "需要衔接的目标审批链id")
    private Long targetId;

    @Schema(description = "所属模块ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模块ID不能为空")
    @AuditField(
            name = "模块名称",
            type = FieldType.RELATION,
            target = "sys_module",
            idField = "id",
            nameField = "module_name")
    private Long moduleId;

    @Schema(description = "上一步步骤")
    private Integer upStep;

    @Schema(description = "当前步骤")
    private Integer currentStep;

    @Schema(description = "下一步步骤")
    private Integer nextStep;

    @Schema(description = "审批链分类ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审批链分类ID不能为空")
    @AuditField(
            name = "审批链分类",
            type = FieldType.RELATION,
            target = "sys_approval_chain_type",
            idField = "id",
            nameField = "title")
    private Long approvalChainTypeId;

    @Schema(description = "审批规则")
    @AuditField(name = "审批规则类型", type = FieldType.DICT, dictCategoryAlias = "approvalRule")
    private String approvalRule;

    @Schema(description = "驳回规则")
    private String rejectRule;

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

    @Schema(description = "当前状态ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "当前状态ID不能为空")
    private Long currentStatusId;

    @Schema(description = "下一状态ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "下一状态ID不能为空")
    private Long nextStatusId;

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
    private List<ButtonConfig> buttonConfigs;

    /** 按钮配置 */
    @Data
    @Schema(description = "按钮配置")
    public static class ButtonConfig {

        @Schema(description = "按钮配置主键ID（编辑时已存在的子项会携带，新增子项为空）")
        private Long id;

        @Schema(description = "操作类型ID")
        @AuditField(
                name = "操作类型",
                type = FieldType.RELATION,
                target = "sys_config_item",
                idField = "id",
                nameField = "label")
        private Long buttonTypeId;

        @Schema(description = "按钮ID")
        @AuditField(
                name = "按钮样式",
                type = FieldType.RELATION,
                target = "sys_button",
                targetClass = ButtonStyleDTO.class)
        private Long buttonId;

        @Schema(description = "日志对应图标")
        @AuditField(
                name = "操作日志图标",
                type = FieldType.DICT,
                dictCategoryAlias = "logStateIcon",
                nameField = "extra")
        private String icon;

        @Schema(description = "日志对应状态描述")
        private String logName;

        @Schema(description = "是否自动进入下一任务 1:是 0:否")
        private Integer autoNextTaskFlag;

        @Schema(description = "消息模板ID集合")
        @AuditField(
                name = "消息类型",
                type = FieldType.RELATION,
                target = "sys_wechat_template",
                idField = "id",
                nameField = "template_title")
        private String msgTemplateIds;
    }
}
