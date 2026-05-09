package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionTargetReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionTargetSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionTargetResp;
import java.util.List;

/** 数据权限目标 API */
public interface SysDataPermissionTargetApi {

    /** 新增/编辑数据权限目标 */
    void saveDataPermissionTarget(SysDataPermissionTargetSaveReq req);

    /** 删除数据权限目标 */
    void deleteDataPermissionTarget(Long id);

    /** 批量新增数据权限目标 */
    void batchSaveDataPermissionTarget(
            Long roleDataPermissionId, List<SysDataPermissionTargetSaveReq> targets);

    /** 查询数据权限目标列表 */
    List<SysDataPermissionTargetResp> listDataPermissionTarget(QuerySysDataPermissionTargetReq req);

    /** 查询数据权限目标详情 */
    SysDataPermissionTargetResp getDataPermissionTarget(Long id);
}
