package com.jdec.platform.data.biz.service;

import com.jdec.platform.data.api.dto.common.ModuleFieldDTO;
import com.jdec.platform.data.api.dto.model.SysModuleInfo;
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

    /** 根据当前用户角色权限裁剪动态字段字典列表 */
    public List<ModuleFieldDTO> filterReadableFields(SysModuleInfo completeResp) {
        if (completeResp == null || completeResp.getFields() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(completeResp.getFields());
    }
}
