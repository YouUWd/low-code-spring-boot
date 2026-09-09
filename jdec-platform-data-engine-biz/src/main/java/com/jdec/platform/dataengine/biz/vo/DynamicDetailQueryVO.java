package com.jdec.platform.dataengine.biz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** 详情接口前端显示VO类 整个data字段是一个Map , 所有field用put的形式作为返回数据的一个key合并返回 */
@Data
@Builder
public class DynamicDetailQueryVO {

    /** put("showSaveBtn" , showSaveBtn) 不带审批链时底部保存按钮通过此标识显隐，带审批的通过按钮返回进行显示 */
    @Schema(description = "保存按钮是否显示")
    private Boolean showSaveBtn;

    /** 属性只做展示说明用 addAll(moduleData) 在data中key不是moduleData，以addAll(moduleData)的形式加入到返回列表 */
    @Schema(description = "业务模块数据")
    private Map<Long, DetailInfoVO> moduleData;

    // 审批链相关数据
    /** put("createdDate" , createdDate) */
    @Schema(description = "审批链创建时间")
    private LocalDateTime createdDate;

    /** put("approvalStatus" , approvalStatus) 带样式的当前状态字段 */
    @Schema(description = "审批链状态字段")
    private Object approvalStatus;

    /** put("canEdit" , canEdit) 可编辑标志位 */
    @Schema(description = "审批链使用，当前页面是否可编辑，通过当前审批状态和操作人的角色等判断得出")
    private Integer canEdit;

    /** put("versionNo" , versionNo) 单据版本 */
    @Schema(description = "审批链使用，当前版本")
    private Integer versionNo;

    /** put("realTimeSaveFlag" , realTimeSaveFlag) 实时保存标志位 */
    @Schema(description = "审批链使用，是否可以实时保存")
    private Boolean realTimeSaveFlag;

    /** put("versionList" , versionList) 版本列表信息字段 */
    @Schema(description = "版本列表信息")
    private List<VersionInfo> versionList;

    /** put("businessNumber" , businessNumber) 单据业务编号 */
    @Schema(description = "单据业务编号")
    private String businessNumber;

    /** put("step" , step) 审批链步骤 */
    @Schema(description = "审批链步骤信息")
    private List<ApprovalStepInfo> step;

    /** put("createUser" , createUser) 创建人信息 */
    @Schema(description = "创建人、申请人信息")
    private UserInfo createUser;

    /**
     * put("btnList" , btnList) 详情底部显示按钮，不带审批链的为简单按钮，前端可能通过保存标志位自己摆放按钮(保存)，带审批的为审批链接口返回，带CSS样式的操作按钮
     */
    @Schema(description = "底部按钮集合")
    private List<Object> btnList;
}
