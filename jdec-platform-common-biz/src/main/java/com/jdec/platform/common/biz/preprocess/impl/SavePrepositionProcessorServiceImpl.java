package com.jdec.platform.common.biz.preprocess.impl;

import com.jdec.platform.common.biz.preprocess.ModuleDataPrepositionProcessor;
import com.jdec.platform.common.biz.preprocess.SavePrepositionProcessorService;
import com.jdec.platform.config.api.dto.common.ModuleTreeDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.shared.context.AppContext;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class SavePrepositionProcessorServiceImpl implements SavePrepositionProcessorService {

    private final List<ModuleDataPrepositionProcessor> moduleDataPrepositionProcessors;

    private final ConfigApi configApi;

    @Override
    public void preProcess(NestedModuleDataDTO nestedModuleDataDTO) {
        Long moduleId = nestedModuleDataDTO.getModuleId();
        ModuleTreeDTO moduleTreeDTO =
                configApi.getModuleTree(
                        AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);
        while (moduleTreeDTO.getRootModule().getParentId() != null
                && moduleTreeDTO.getRootModule().getParentId() > 0) {
            moduleId = moduleTreeDTO.getRootModule().getParentId();
            moduleTreeDTO =
                    configApi.getModuleTree(
                            AppContext.getProjectNo(), AppContext.getSubjectId(), moduleId);
        }
        processModuleDataPreposition(nestedModuleDataDTO, moduleTreeDTO);
    }

    private void processModuleDataPreposition(
            NestedModuleDataDTO nestedModuleDataDTO, ModuleTreeDTO moduleTreeDTO) {
        moduleDataPrepositionProcessors.stream()
                .filter(
                        moduleDataPrepositionProcessor ->
                                moduleDataPrepositionProcessor.isApplicable(
                                        nestedModuleDataDTO.getModuleId(), moduleTreeDTO))
                .forEach(
                        moduleDataPrepositionProcessor ->
                                moduleDataPrepositionProcessor.preProcess(
                                        nestedModuleDataDTO, moduleTreeDTO));
        List<RecordDataDTO> recordDataDTOList =
                nestedModuleDataDTO.getRecord() != null
                        ? nestedModuleDataDTO.getRecord()
                        : Collections.emptyList();
        for (RecordDataDTO recordDataDTO : recordDataDTOList) {
            List<NestedModuleDataDTO> nestedModuleDataDTOList =
                    recordDataDTO.getChildren() != null
                            ? recordDataDTO.getChildren()
                            : Collections.emptyList();
            for (NestedModuleDataDTO child : nestedModuleDataDTOList) {
                processModuleDataPreposition(child, moduleTreeDTO);
            }
        }
    }
}
