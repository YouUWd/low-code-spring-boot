package com.jdec.platform.externalobject.biz.service;

import com.jdec.platform.externalobject.api.dto.request.CompanyBadgeNumReq;
import com.jdec.platform.externalobject.api.dto.request.CompanyRegStatusUpdateReq;
import com.jdec.platform.externalobject.api.dto.response.CompanyTreeNodeResp;
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

    /**
     * 根据单据物理主键ID获取对象性质 (nature)
     *
     * @param id 当前单据物理主键ID
     * @return 对象性质 (已解析的结构化数据或原始配置)
     */
    Object getNature(Long id);

    Map<Long, Long> getMenuNum(CompanyBadgeNumReq req);

    /**
     * 基于单据ID获取关联的公司层级树
     *
     * <p>业务规则：
     *
     * <ul>
     *   <li>以当前单据版本为锚点，向上回溯至顶级根节点，向下发散至最底层节点；
     *   <li>父子节点挂接严格基于 {@code parent_id} 中的 {@code value} (业务编号) 与 {@code version_no} (快照版本号) 精确匹配；
     *   <li>同一企业多个版本时，当前单据锁定其指定版本，其余下级企业优先选择已生效(99)且版本号最大的记录。
     * </ul>
     *
     * @param id 当前单据物理主键ID
     * @return 完整公司层级树根节点
     */
    CompanyTreeNodeResp getCompanyTree(Long id);
}
