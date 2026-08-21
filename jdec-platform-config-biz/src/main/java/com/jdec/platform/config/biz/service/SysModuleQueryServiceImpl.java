package com.jdec.platform.config.biz.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jdec.platform.config.api.SysModuleApi;
import com.jdec.platform.config.api.SysModuleQueryApi;
import com.jdec.platform.config.api.dto.response.SysModuleCompleteResp;
import com.jdec.platform.config.biz.entity.SysModule;
import com.jdec.platform.config.biz.mapper.SysModuleMapper;
import com.jdec.platform.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysModuleQueryServiceImpl implements SysModuleQueryApi {

    private final SysModuleMapper sysModuleMapper;
    private final SysModuleApi sysModuleApi;

    @Override
    public SysModuleCompleteResp getModuleCompleteByCode(
            String projectNo, Long subjectId, String moduleCode) {
        SysModule module =
                sysModuleMapper.selectOne(
                        Wrappers.<SysModule>lambdaQuery()
                                .eq(SysModule::getProjectNo, projectNo)
                                .eq(SysModule::getSubjectId, subjectId)
                                .eq(SysModule::getModuleCode, moduleCode));

        if (module == null) {
            throw new BusinessException("模块不存在: " + moduleCode);
        }

        return sysModuleApi.getModuleCompleteById(projectNo, subjectId, module.getId());
    }
}
