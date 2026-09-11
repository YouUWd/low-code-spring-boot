package com.jdec.platform.dataengine.biz.service;

import com.jdec.platform.config.api.dto.response.SysModuleResp;
import com.jdec.platform.dataengine.biz.dto.ModuleTableHeaderDTO;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 权限与表头过滤服务 负责字段读写权限判定与数据权限行级条件注入 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionFilterService {

    /** 根据当前用户角色权限裁剪动态表头列表 */
    public List<ModuleTableHeaderDTO> filterReadableHeaders(SysModuleResp completeResp) {
        if (completeResp == null || completeResp.getModule() == null) {
            return new ArrayList<>();
        }
        //        List<ModuleTableHeaderDTO> originalHeaders =
        // completeResp.getModule().getTableHeader();
        // TODO 从菜单配置获取表头配置
        List<ModuleTableHeaderDTO> originalHeaders = new ArrayList<>();
        if (originalHeaders == null || originalHeaders.isEmpty()) {
            return new ArrayList<>();
        }

        // 当前版本默认全部可读（后续对接 SysRoleModuleFieldPermissionApi 权限校验）
        return new ArrayList<>(originalHeaders);
    }

    /** 校验待保存物理表的数据是否有越权写入字段 */
    public void validateWritableFields(Long moduleId, String tableName, List<String> fieldNames) {
        // 当前版本通过校验（后续对接 SysRoleModuleFieldPermissionApi 进行 writable / updatable 校验）
        log.debug(
                "校验写入字段权限: moduleId={}, tableName={}, fields={}", moduleId, tableName, fieldNames);
    }
}
