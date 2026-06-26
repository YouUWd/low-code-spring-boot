package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionMappingReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionMappingSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionMappingResp;
import java.util.List;

/** 数据权限 API */
public interface SysDataPermissionMappingApi {

    /** 新增/编辑数据权限 */
    void saveDataPermissionMapping(SysDataPermissionMappingSaveReq req);

    /** 删除数据权限 */
    void deleteDataPermissionMapping(Long id);

    /** 批量新增数据权限 */
    void batchSaveDataPermissionMapping(List<SysDataPermissionMappingSaveReq> permissions);

    /** 查询数据权限列表 */
    List<SysDataPermissionMappingResp> listDataPermissionMapping(
            QuerySysDataPermissionMappingReq req);

    /** 查询数据权限详情 */
    SysDataPermissionMappingResp getDataPermissionMapping(Long id);

    /** 查询权限分类列表（type_code/type_name 去重） */
    List<SysDataPermissionMappingResp> listDataPermissionCategories();
}
