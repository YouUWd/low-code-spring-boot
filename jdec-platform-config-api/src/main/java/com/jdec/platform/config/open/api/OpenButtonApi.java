package com.jdec.platform.config.open.api;

import com.jdec.platform.config.api.dto.response.SysButtonResp;
import java.util.List;

/**
 * 按钮开放接口（供业务端查询系统配置中的按钮列表）
 *
 * <p>提供按钮维度的只读查询能力，调用方无需关心按钮的存储细节。
 */
public interface OpenButtonApi {

    /**
     * 查询当前项目 + 主体下的按钮列表（不分页）
     *
     * <p>数据隔离取当前请求上下文（{@code X-Project-No} 请求头对应的项目编码 + 主体ID），按排序值升序返回。
     *
     * @return 按钮列表
     */
    List<SysButtonResp> listButtons();
}
