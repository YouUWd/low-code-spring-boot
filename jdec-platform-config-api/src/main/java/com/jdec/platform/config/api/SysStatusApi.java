package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.SysStatusDragReq;
import com.jdec.platform.config.api.dto.request.SysStatusSaveReq;
import com.jdec.platform.config.api.dto.response.SysStatusTreeResp;
import java.util.List;

public interface SysStatusApi {

    /** 查询状态树 */
    List<SysStatusTreeResp> tree();

    /** 新增/编辑状态 */
    void saveStatus(SysStatusSaveReq req);

    /** 删除状态（级联删除子节点） */
    void deleteStatus(Long id, Long subjectId);

    /** 拖拽状态 */
    void dragStatus(SysStatusDragReq req);
}
