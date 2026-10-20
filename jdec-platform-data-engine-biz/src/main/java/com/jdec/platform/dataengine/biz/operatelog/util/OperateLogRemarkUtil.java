package com.jdec.platform.dataengine.biz.operatelog.util;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.operatelog.convert.OperateLogRemarkConverter;
import com.jdec.platform.dataengine.biz.operatelog.dto.OperateLogRemarkDTO;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 操作日志 remark 生成工具。
 *
 * <p>对比「保存前的旧数据」与「本次保存提交的新数据」，生成 remark 的 JSON 字符串：
 *
 * <ul>
 *   <li>无审批链：{@code {"change":[...]}}
 *   <li>有审批链：{@code {"comment":"审批原因","change":[...]}}（comment 为空时退化为无审批链结构）
 * </ul>
 *
 * <p>无任何变更时返回 {@code null}，调用方据此跳过日志记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperateLogRemarkUtil {

    private final OperateLogRemarkConverter operateLogRemarkConverter;

    private final ObjectMapper objectMapper;

    /**
     * 生成 remark JSON 串（无审批链）：{@code {"change":[...]}}。
     *
     * @param oldData 保存前的旧数据（纯新增时为 null）
     * @param newData 本次保存提交的数据
     * @param moduleCacheMap 模块元数据（含字段、唯一标识、子表、模块名）
     * @param menuId 菜单ID（取变更项名称，可为 null）
     * @return remark JSON 串；无任何变更时返回 null
     */
    public String buildRemark(
            NestedModuleDataDTO oldData,
            NestedModuleDataDTO newData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long menuId) {
        return buildRemark(oldData, newData, moduleCacheMap, menuId, null, true);
    }

    /**
     * 生成 remark JSON 串（有审批链）：{@code {"comment":"审批原因","change":[...]}}。
     *
     * @param oldData 保存前的旧数据（纯新增时为 null）
     * @param newData 本次保存提交的数据
     * @param moduleCacheMap 模块元数据（含字段、唯一标识、子表、模块名）
     * @param menuId 菜单ID（取变更项名称，可为 null）
     * @param comment 审批原因；为空时退化为无审批链结构 {@code {"change":[...]}}
     * @return remark JSON 串；无任何变更时返回 null
     */
    public String buildRemark(
            NestedModuleDataDTO oldData,
            NestedModuleDataDTO newData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long menuId,
            String comment) {
        return buildRemark(oldData, newData, moduleCacheMap, menuId, comment, false);
    }

    /**
     * @param preserveJsonValues 是否保持字段值的原始类型（数组/对象不再字符串化）；无审批链为 true
     */
    private String buildRemark(
            NestedModuleDataDTO oldData,
            NestedModuleDataDTO newData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long menuId,
            String comment,
            boolean preserveJsonValues) {
        OperateLogRemarkDTO remark =
                operateLogRemarkConverter.convert(
                        oldData, newData, moduleCacheMap, menuId, preserveJsonValues);
        if (remark == null || CollectionUtil.isEmpty(remark.getChange())) {
            return null;
        }
        if (StringUtils.isNotBlank(comment)) {
            remark.setComment(comment);
        }
        try {
            return objectMapper.writeValueAsString(remark);
        } catch (Exception e) {
            log.error("操作日志 remark 序列化失败", e);
            throw new BusinessException("操作日志生成失败：remark 格式不正确");
        }
    }

    /**
     * 将 remark JSON 串按 {@link OperateLogRemarkDTO} 的结构解析回对象（{@link #buildRemark} 的逆操作）。
     *
     * @param remarkJson remark JSON 串（{@code {"change":[...]}} 或 {@code
     *     {"comment":"...","change":[...]}}）
     * @return 解析结果；入参为空或格式非法时返回 {@code null}（不影响调用方主流程）
     */
    public OperateLogRemarkDTO parse(String remarkJson) {
        if (StringUtils.isBlank(remarkJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(remarkJson, OperateLogRemarkDTO.class);
        } catch (Exception e) {
            log.warn("操作日志 remark 解析失败: remarkJson={}", remarkJson, e);
            return null;
        }
    }
}
