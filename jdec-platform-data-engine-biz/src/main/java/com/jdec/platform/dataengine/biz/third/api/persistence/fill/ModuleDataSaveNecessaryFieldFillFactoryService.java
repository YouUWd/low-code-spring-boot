package com.jdec.platform.dataengine.biz.third.api.persistence.fill;

import com.jdec.platform.dataengine.biz.dto.context.RecordSaveContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModuleDataSaveNecessaryFieldFillFactoryService {

    private List<BaseDataSaveNecessaryFieldFillService> baseDataSaveNecessaryFieldFillServices;

    public void fillData(RecordSaveContext recordSaveContext) {
        baseDataSaveNecessaryFieldFillServices.forEach(
                service -> {
                    if (service.isReferTo(recordSaveContext.getSaveType())) {
                        service.fillData(recordSaveContext);
                    }
                });
    }
}
