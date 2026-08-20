package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.QuerySysRightReq;
import com.jdec.platform.config.api.dto.request.SysRightSaveReq;
import com.jdec.platform.config.api.dto.response.SysRightOptionResp;
import com.jdec.platform.config.api.dto.response.SysRightResp;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;

public interface SysRightApi {

    /** 新增/编辑权限节点 */
    void saveRight(SysRightSaveReq req);

    /** 删除权限节点 */
    void deleteRight(Long id);

    /** 查询父级权限节点下拉列表 */
    List<SysRightOptionResp> listParentOptions(Integer nodeType);

    /** 查询权限节点列表（分页） */
    PageResult<SysRightResp> listRight(QuerySysRightReq req);
}
