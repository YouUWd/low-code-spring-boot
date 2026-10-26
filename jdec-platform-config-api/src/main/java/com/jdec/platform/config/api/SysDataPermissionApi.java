package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysDataPermissionReq;
import com.jdec.platform.config.api.dto.request.SysDataPermissionSaveReq;
import com.jdec.platform.config.api.dto.response.SysDataPermissionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import java.util.List;

/** 数据权限节点 API */
public interface SysDataPermissionApi {

    /** 新增/编辑数据权限节点 */
    SysRightResp saveDataPermission(SysDataPermissionSaveReq req);

    /** 查询数据权限节点列表，并调用业务系统接口查询关联的业务数据 */
    List<SysDataPermissionResp> listDataPermission(QuerySysDataPermissionReq req);
}
