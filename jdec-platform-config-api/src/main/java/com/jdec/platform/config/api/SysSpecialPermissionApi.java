package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysSpecialPermissionReq;
import com.jdec.platform.config.api.dto.request.SysSpecialPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysSpecialPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysSpecialPermissionResp;
import java.util.List;

/** 特殊权限 API */
public interface SysSpecialPermissionApi {

    /** 新增/编辑特殊权限 */
    void saveSpecialPermission(SysSpecialPermissionSaveReq req);

    /**
     * 删除特殊权限
     *
     * @param id 特殊权限ID
     * @param force 是否强制删除。false 时若有角色关联则抛出提示；true 时级联删除
     */
    void deleteSpecialPermission(Long id, boolean force);

    /** 查询特殊权限下拉列表 */
    List<SysSpecialPermissionOptionResp> listSpecialPermissionOptions();

    /** 查询特殊权限列表 */
    List<SysSpecialPermissionResp> listSpecialPermission(QuerySysSpecialPermissionReq req);

    /** 查询特殊权限详情 */
    SysSpecialPermissionResp getSpecialPermission(Long id);
}
