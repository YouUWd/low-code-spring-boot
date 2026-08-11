package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.SysProjectSubjectResp;
import java.util.List;

public interface SysProjectSubjectApi {

    /** 查询所有项目主体（不分页） */
    List<SysProjectSubjectResp> list();

    /** 根据ID查询项目主体 */
    SysProjectSubjectResp getById(Long id);

    /** 删除项目主体 */
    void deleteProjectSubject(Long id);
}
