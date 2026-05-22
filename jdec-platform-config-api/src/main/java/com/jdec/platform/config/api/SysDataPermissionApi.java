package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import java.util.List;

/** 数据权限 API */
public interface SysDataPermissionApi {

    /** 新增/编辑数据权限 */
    void saveDataPermission(SysDataPermissionSaveReq req);

    /** 删除数据权限 */
    void deleteDataPermission(Long id);

    /** 批量新增数据权限 */
    void batchSaveDataPermission(List<SysDataPermissionSaveReq> permissions);

    /** 查询数据权限列表 */
    List<SysDataPermissionResp> listDataPermission(QuerySysDataPermissionReq req);

    /** 查询数据权限详情 */
    SysDataPermissionResp getDataPermission(Long id);

    /** 查询权限分类列表（type_code/type_name 去重） */
    List<SysDataPermissionResp> listDataPermissionCategories();
}
