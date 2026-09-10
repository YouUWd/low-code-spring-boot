package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SaveStatusReq;
import com.jdec.platform.config.api.dto.request.SysStatusDragReq;
import com.jdec.platform.config.api.dto.request.SysStatusToggleReq;
import com.jdec.platform.config.api.dto.response.ModuleApprovalChainConfigResp;
import com.jdec.platform.config.api.dto.response.SysStatusTreeResp;
import java.util.List;

public interface SysStatusApi {

    /** 查询状态树 */
    List<SysStatusTreeResp> tree();

    /**
     * 新增/编辑状态，新增时返回新记录ID。
     *
     * <p>支持前端将整棵状态树一并提交：当 request.treeNodes 非空时，保存当前状态节点后按 treeNodes 差量同步整棵树的父子关系与同级排序；treeNodes
     * 中缺失的节点不做删除。
     */
    Long saveStatus(SaveStatusReq request);

    /**
     * 删除状态（级联删除子节点）
     *
     * @param id 状态ID
     * @param force 是否强制删除。false 时若有子状态则抛出提示；true 时级联删除
     */
    void deleteStatus(Long id, boolean force);

    /** 拖拽状态 */
    void dragStatus(SysStatusDragReq req);

    /**
     * 停用/启用状态
     *
     * @param req 状态切换请求（包含状态ID和启用标志）
     */
    void toggleStatus(SysStatusToggleReq req);

    ModuleApprovalChainConfigResp.StatusNode getStatusByValue(Integer statusValue, Long moduleId);
}
