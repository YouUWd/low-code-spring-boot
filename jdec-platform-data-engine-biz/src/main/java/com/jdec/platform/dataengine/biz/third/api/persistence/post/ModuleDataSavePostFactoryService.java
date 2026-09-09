package com.jdec.platform.dataengine.biz.third.api.persistence.post;

import com.jdec.platform.dataengine.biz.dto.context.ModuleDataPostContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModuleDataSavePostFactoryService {

    private final List<BaseDataSavePost> baseDataSavePostList;

    public Boolean postProcess(ModuleDataPostContext context) {
        return baseDataSavePostList.stream()
                .filter(post -> post.isReferTo(context.getSaveType()))
                .anyMatch(post -> post.postProcess(context));
    }
}
