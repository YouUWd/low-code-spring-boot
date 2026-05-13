package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.CreateWechatTemplateParamReq;
import com.jdec.platform.config.api.dto.request.CreateWechatTemplateReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateParamReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateReq;
import com.jdec.platform.config.api.dto.request.WechatTemplateParamQueryReq;
import com.jdec.platform.config.api.dto.request.WechatTemplateQueryReq;
import com.jdec.platform.config.api.dto.response.WechatTemplateDetailResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateEntryResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateParamEntryResp;
import com.jdec.platform.shared.model.PageResult;

/** 企业微信消息通知模板 API 接口 (含模板参数管理) */
public interface SysWechatTemplateApi {

    // --- 模板主表管理 ---

    /**
     * 分页查询消息通知模板
     *
     * @param query 查询参数
     * @return 模板列表分页结果
     */
    PageResult<WechatTemplateEntryResp> listTemplates(WechatTemplateQueryReq query);

    /**
     * 获取模板详情
     *
     * @param id 模板 ID
     * @return 模板详情响应
     */
    WechatTemplateDetailResp getTemplateById(Long id);

    /**
     * 创建消息通知模板
     *
     * @param req 创建请求
     * @return 创建后的模板详情响应
     */
    WechatTemplateDetailResp createTemplate(CreateWechatTemplateReq req);

    /**
     * 更新消息通知模板
     *
     * @param req 更新请求
     * @return 更新后的模板详情响应
     */
    WechatTemplateDetailResp updateTemplate(UpdateWechatTemplateReq req);

    /**
     * 删除消息通知模板 (逻辑删除)
     *
     * @param id 模板 ID
     */
    void deleteTemplate(Long id);

    // --- 模板参数管理 ---

    /**
     * 分页查询微信模板参数
     *
     * @param query 查询请求
     * @return 分页结果
     */
    PageResult<WechatTemplateParamEntryResp> listParams(WechatTemplateParamQueryReq query);

    /**
     * 获取微信模板参数详情
     *
     * @param id 参数 ID
     * @return 参数详情
     */
    WechatTemplateParamEntryResp getParamById(Long id);

    /**
     * 创建微信模板参数
     *
     * @param req 创建请求
     * @return 创建后的参数详情
     */
    WechatTemplateParamEntryResp createParam(CreateWechatTemplateParamReq req);

    /**
     * 更新微信模板参数
     *
     * @param req 更新请求
     * @return 更新后的参数详情
     */
    WechatTemplateParamEntryResp updateParam(UpdateWechatTemplateParamReq req);

    /**
     * 删除微信模板参数 (逻辑删除)
     *
     * @param id 参数 ID
     */
    void deleteParam(Long id);
}
