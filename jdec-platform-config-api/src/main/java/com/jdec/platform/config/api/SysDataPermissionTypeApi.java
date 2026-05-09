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

    /** 删除数据权限类型 */
    void deleteDataPermissionType(Long id);

    /** 查询数据权限类型下拉列表 */
    List<SysDataPermissionTypeOptionResp> listDataPermissionTypeOptions();

    /** 查询数据权限类型列表 */
    List<SysDataPermissionTypeResp> listDataPermissionType(QuerySysDataPermissionTypeReq req);

    /** 查询数据权限类型详情 */
    SysDataPermissionTypeResp getDataPermissionType(Long id);
}
