package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.response.SysModuleFieldTreeResp;
import java.util.List;

/** 模块字段权限管理 API 接口 */
public interface SysModuleFieldPermissionApi {

    /**
     * 获取纯净的模块-表-字段树形结构
     *
     * @param projectNo 项目编码
     * @param subjectId 主体ID
     * @return 模块表字段树列表
     */
    List<SysModuleFieldTreeResp> getModuleTableFieldTree(String projectNo, Long subjectId);
}
