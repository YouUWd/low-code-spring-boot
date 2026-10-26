package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SysInteractionPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionDetailResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionListResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionResp;
import com.jdec.platform.config.api.dto.response.SysModuleInteractionPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import java.util.List;

/** 交互权限 API */
public interface SysInteractionPermissionApi {

    /** 新增/编辑交互权限 */
    SysRightResp saveInteractionPermission(SysInteractionPermissionSaveReq req);

    /**
     * 删除交互权限
     *
     * @param id 交互权限ID
     * @param force 是否强制删除。false 时若有角色关联则抛出提示；true 时级联删除
     */
    void deleteInteractionPermission(Long id, boolean force);

    /** 查询交互权限列表 */
    List<SysInteractionPermissionListResp> listInteractionPermission(
            QuerySysInteractionPermissionReq req);

    /** 查询交互权限详情 */
    SysInteractionPermissionDetailResp getInteractionPermission(Long id);

    /** 查询交互权限列表（扁平化） */
    List<SysInteractionPermissionResp> listAllInteractionPermissions();

    /** 查询模块树及每个模块各类型关联的交互权限 */
    List<SysModuleInteractionPermissionResp> listModuleInteractionPermissionTree(Integer category);
}
