package com.jdec.platform.common.biz.postprocess.enhance.provider;

import com.jdec.platform.common.biz.postprocess.enhance.context.DataRowEnhanceContext;
import com.jdec.platform.data.api.dto.common.DataRow;
import java.util.List;

/**
 * 物理数据行增强提供者扩展点 (DataRowEnhanceProvider)
 *
 * <p>遵循面向对象规范。针对不同字段/业务类型的数据行对象化增强（如用户、状态、字典等）， 实现此接口并声明为 @Component 即可自动接入装配链路。
 */
public interface DataRowEnhanceProvider {

    /** 执行优先级，数值越小越先执行 */
    default int order() {
        return 0;
    }

    /**
     * 判断当前上下文是否支持此提供者处理
     *
     * @param ctx 数据行增强上下文
     * @return true-启用, false-跳过
     */
    default boolean supports(DataRowEnhanceContext ctx) {
        return true;
    }

    /**
     * 对当前层级的数据行集合执行批量增强装配
     *
     * @param records 当前层级的数据行集合
     * @param ctx 数据行增强上下文
     */
    void enhance(List<DataRow> records, DataRowEnhanceContext ctx);
}
