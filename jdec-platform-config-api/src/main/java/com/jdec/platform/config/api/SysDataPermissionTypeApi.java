package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionTypeReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionTypeSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTypeOptionResp;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTypeResp;
import java.util.List;

/** 数据权限类型 API */
public interface SysDataPermissionTypeApi {

    /** 新增/编辑数据权限类型 */
    void saveDataPermissionType(SysDataPermissionTypeSaveReq req);

    /**
     * 删除数据权限类型
     *
     * @param id 数据权限类型ID
     * @param force 是否强制删除。false 时若有角色关联则抛出提示；true 时级联删除
     */
    void deleteDataPermissionType(Long id, boolean force);

    /** 查询数据权限类型下拉列表 */
    List<SysDataPermissionTypeOptionResp> listDataPermissionTypeOptions();

    /** 查询数据权限类型列表 */
    List<SysDataPermissionTypeResp> listDataPermissionType(QuerySysDataPermissionTypeReq req);

    /** 查询数据权限类型详情 */
    SysDataPermissionTypeResp getDataPermissionType(Long id);
}
