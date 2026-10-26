package com.jdec.platform.externalobject.api;

import com.jdec.platform.externalobject.api.dto.request.ExternalObjectFieldQuery;
import java.util.Set;

/**
 * 外部对象数据查询接口。
 *
 * <p>供业务校验等场景查询已保存的外部对象数据；实现方负责数据库访问。
 */
public interface ExternalObjectQueryApi {

    /**
     * 按字段值查重：返回已保存数据中存在的字段值。
     *
     * <p>查询会排除 {@code excludeBusinessNo} 对应的外部对象（含其历史版本），用于变更申请单编辑时排除自身。
     *
     * @param query 查询条件；{@code values} 为空时返回空集合
     * @return 命中的字段值（去重）
     */
    Set<String> findDuplicateValues(ExternalObjectFieldQuery query);
}
