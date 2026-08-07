package com.jdec.platform.config.api;

import com.jdec.platform.config.api.dto.request.CreateWechatTemplateParamReq;
import com.jdec.platform.config.api.dto.request.CreateWechatTemplateReq;
import com.jdec.platform.config.api.dto.request.SendWechatMessageReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateParamReq;
import com.jdec.platform.config.api.dto.request.UpdateWechatTemplateReq;
import com.jdec.platform.config.api.dto.request.WechatTemplateParamQueryReq;
import com.jdec.platform.config.api.dto.request.WechatTemplateQueryReq;
import com.jdec.platform.config.api.dto.response.WechatTemplateDetailResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateEntryResp;
import com.jdec.platform.config.api.dto.response.WechatTemplateParamEntryResp;
import com.jdec.platform.shared.model.PageResult;
import java.util.List;

/** 企业微信消息通知模板 API 接口 (含模板参数管理) */
public interface SysWechatTemplateApi {

    // --- 模板主表管理 ---

    /**
     * 分页查询消息通知模板
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param query 查询参数
     * @return 模板列表分页结果
     */
    PageResult<WechatTemplateEntryResp> listTemplates(
            String projectNo, Long subjectId, WechatTemplateQueryReq query);

    /**
     * 根据模块ID查询微信模板列表
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param moduleId 模块 ID
     * @return 微信模板列表
     */
    List<WechatTemplateEntryResp> listTemplatesByModuleId(
            String projectNo, Long subjectId, Long moduleId);

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
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param req 创建请求
     * @return 创建后的模板详情响应
     */
    WechatTemplateDetailResp createTemplate(
            String projectNo, Long subjectId, CreateWechatTemplateReq req);

    /**
     * 更新消息通知模板
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param req 更新请求
     * @return 更新后的模板详情响应
     */
    WechatTemplateDetailResp updateTemplate(
            String projectNo, Long subjectId, UpdateWechatTemplateReq req);

    /**
     * 删除消息通知模板 (逻辑删除)
     *
     * @param id 模板 ID
     */
    void deleteTemplate(Long id);

    /**
     * 测试发送微信模板消息 (向当前登录用户发送模板消息预览)
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param id 模板 ID
     * @return 是否成功
     */
    boolean testSendMessage(String projectNo, Long subjectId, Long id);

    // --- 模板参数管理 ---

    /**
     * 分页查询微信模板参数
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param query 查询请求
     * @return 分页结果
     */
    PageResult<WechatTemplateParamEntryResp> listParams(
            String projectNo, Long subjectId, WechatTemplateParamQueryReq query);

    /**
     * 根据模块ID查询微信模板参数列表
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param moduleId 模块 ID
     * @return 微信模板参数列表
     */
    List<WechatTemplateParamEntryResp> listParamsByModuleId(
            String projectNo, Long subjectId, Long moduleId);

    /**
     * 根据模板参数 ID 列表查询微信模板参数列表
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param ids 模板参数 ID 列表
     * @return 微信模板参数列表
     */
    List<WechatTemplateParamEntryResp> listParamsByIds(
            String projectNo, Long subjectId, List<Long> ids);

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
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param req 创建请求
     * @return 创建后的参数详情
     */
    WechatTemplateParamEntryResp createParam(
            String projectNo, Long subjectId, CreateWechatTemplateParamReq req);

    /**
     * 更新微信模板参数
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param req 更新请求
     * @return 更新后的参数详情
     */
    WechatTemplateParamEntryResp updateParam(
            String projectNo, Long subjectId, UpdateWechatTemplateParamReq req);

    /**
     * 删除微信模板参数 (逻辑删除)
     *
     * @param id 参数 ID
     */
    void deleteParam(Long id);

    /**
     * 发送企业微信消息并保存记录
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param req 发送消息请求
     * @return 是否发送成功
     */
    boolean sendWechatMessage(String projectNo, Long subjectId, SendWechatMessageReq req);

    /**
     * 根据模块ID和触发类型查询微信模板
     *
     * @param projectNo 项目编号
     * @param subjectId 主体 ID
     * @param moduleId 模块 ID
     * @param triggerType 触发类型
     * @return 微信模板
     */
    WechatTemplateEntryResp getTemplateByModuleId(
            String projectNo, Long subjectId, Long moduleId, String triggerType);
}
