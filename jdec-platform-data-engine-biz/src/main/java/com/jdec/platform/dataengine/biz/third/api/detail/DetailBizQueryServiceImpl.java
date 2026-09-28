package com.jdec.platform.dataengine.biz.third.api.detail;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DetailQueryContext;
import com.jdec.platform.dataengine.biz.third.api.DetailBizQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetailBizQueryServiceImpl implements DetailBizQueryService {

    private final List<DetailBizReferQueryService> detailBizReferQueryServiceList;

    @Override
    public List<RecordDataDTO> queryDetailBiz(DetailQueryContext context) {
        if (!CollectionUtil.isEmpty(detailBizReferQueryServiceList)) {
            DetailBizReferQueryService service =
                    detailBizReferQueryServiceList.stream()
                            .filter(x -> x.isReferTo(context.getQueryType()))
                            .findFirst()
                            .orElse(null);
            if (service != null) {
                return service.queryDetailBizRefer(context);
            }
        }
        return List.of();
    }
}
