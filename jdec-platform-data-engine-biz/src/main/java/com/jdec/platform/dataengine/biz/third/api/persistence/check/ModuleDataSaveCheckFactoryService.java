package com.jdec.platform.dataengine.biz.third.api.persistence.check;

import com.jdec.platform.dataengine.biz.dto.context.ModuleDataCheckContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModuleDataSaveCheckFactoryService {

    private final List<BaseDataSaveCheck> baseDataSaveCheckList;

    public Boolean check(ModuleDataCheckContext context) {
        return baseDataSaveCheckList.stream()
                .filter(check -> check.isReferTo(context.getSaveType()))
                .allMatch(check -> check.check(context));
    }
}
