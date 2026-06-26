package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionOptionResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import java.util.List;

/** 数据权限节点 API */
public interface SysDataPermissionApi {

    /** 新增/编辑数据权限节点 */
    SysRightResp saveDataPermission(SysDataPermissionSaveReq req);

    /**
     * 删除数据权限节点
     *
     * @param id 数据权限节点ID
     * @param force 是否强制删除。false 时若有角色关联则抛出提示；true 时级联删除
     */
    void deleteDataPermission(Long id, boolean force);

    /** 查询数据权限节点下拉列表 */
    List<SysDataPermissionOptionResp> listDataPermissionOptions();

    /** 查询数据权限节点列表 */
    List<SysDataPermissionResp> listDataPermission(QuerySysDataPermissionReq req);

    /** 查询数据权限节点详情 */
    SysDataPermissionResp getDataPermission(Long id);
}
