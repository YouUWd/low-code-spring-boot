package com.jdec.platform.common.biz.postprocess.enhance.provider;

import com.jdec.platform.common.biz.postprocess.enhance.context.OptionEnhanceContext;
import com.jdec.platform.data.api.dto.response.DataOptionResp;
import java.util.List;

/**
 * 候选项数据增强提供者扩展点 (OptionEnhanceProvider)
 *
 * <p>遵循面向对象规范。针对不同字段/业务类型的候选项增强（如用户对象转换、状态转换、行业树过滤、词云去重等）， 实现此接口并声明为 @Component 即可自动接入装配链路。
 */
public interface OptionEnhanceProvider {

    /** 执行优先级，数值越小越先匹配执行 */
    default int order() {
        return 0;
    }

    /**
     * 判断当前候选项上下文是否支持此提供者处理
     *
     * @param ctx 候选项增强上下文
     * @return true-启用, false-跳过
     */
    boolean supports(OptionEnhanceContext ctx);

    /**
     * 对原始候选项集合执行增强装配
     *
     * @param rawOptions 底层返回的原始候选项列表
     * @param ctx 候选项增强上下文
     * @return 增强后的候选项列表
     */
    List<DataOptionResp> enhance(List<DataOptionResp> rawOptions, OptionEnhanceContext ctx);
}
