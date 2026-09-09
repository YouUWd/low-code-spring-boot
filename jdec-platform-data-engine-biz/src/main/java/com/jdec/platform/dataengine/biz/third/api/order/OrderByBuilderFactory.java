package com.jdec.platform.dataengine.biz.third.api.order;

import com.jdec.platform.dataengine.biz.dto.context.StatementContext;
import java.util.ArrayList;
import java.util.List;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class OrderByBuilderFactory {

    public List<OrderField<?>> buildOrderBy(StatementContext context) {
        List<OrderField<?>> orderFields = new ArrayList<>();
        if (!CollectionUtils.isEmpty(context.getOrder())) {
            context.getOrder().stream()
                    .forEach(
                            ele -> {
                                if ("ASC".equalsIgnoreCase(ele.getValue())) {
                                    for (String fieldName : ele.getField()) {
                                        orderFields.add(
                                                DSL.field(DSL.name(ele.getTableName(), fieldName)));
                                    }
                                } else {
                                    for (String fieldName : ele.getField()) {
                                        orderFields.add(
                                                DSL.field(DSL.name(ele.getTableName(), fieldName))
                                                        .desc());
                                    }
                                }
                            });
        }
        return orderFields;
    }
}
