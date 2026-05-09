package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysInteractionPermissionReq;
import com.jdec.platform.config.api.dto.request.SysInteractionPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionDetailResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionListResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysInteractionPermissionResp;
import java.util.List;

/** 交互权限 API */
public interface SysInteractionPermissionApi {

    /** 新增/编辑交互权限 */
    void saveInteractionPermission(SysInteractionPermissionSaveReq req);

    /** 删除交互权限 */
    void deleteInteractionPermission(Long id);

    /** 查询父级交互权限下拉列表 */
    List<SysInteractionPermissionOptionResp> listParentInteractionPermissionOptions();

    /** 查询交互权限列表 */
    List<SysInteractionPermissionListResp> listInteractionPermission(
            QuerySysInteractionPermissionReq req);

    /** 查询交互权限详情 */
    SysInteractionPermissionDetailResp getInteractionPermission(Long id);

    /** 查询交互权限树形结构 */
    List<SysInteractionPermissionResp> listInteractionPermissionTree();
}
