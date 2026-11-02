package com.jdec.platform.externalobject.biz.service;

import com.jdec.platform.externalobject.api.dto.request.CompanyBadgeNumReq;
import com.jdec.platform.externalobject.api.dto.request.CompanyRegStatusUpdateReq;
import java.util.Map;

/** 外部对象特定业务定制服务接口 */
public interface CompanyCustomService {

    /**
     * 修改单据的登记状态
     *
     * <p>业务规则：
     *
     * <ul>
     *   <li>仅支持修改处于【已启用 (99)】或【已取消 (-99)】状态的单据；
     *   <li>针对目标对象性质（nature）执行登记状态可选值规则校验；
     *   <li>更新目标单据的 reg_status、updated_by 与 updated_date。
     * </ul>
     *
     * @param req 修改登记状态请求体 (通过单据物理主键 id 定位)
     */
    void updateRegStatus(CompanyRegStatusUpdateReq req);

    Map<Long, Long> getMenuNum(CompanyBadgeNumReq req);
}
