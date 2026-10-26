package com.jdec.platform.dataengine.biz.operatelog.util;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdec.platform.config.api.dto.common.FieldWordCloudDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.biz.dto.SysModuleCombineInfo;
import com.jdec.platform.dataengine.biz.operatelog.convert.OperateLogRemarkConverter;
import com.jdec.platform.dataengine.biz.operatelog.dto.OperateLogRemarkDTO;
import com.jdec.platform.shared.context.AppContext;
import com.jdec.platform.shared.exception.BusinessException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 操作日志 remark 生成工具。
 *
 * <p>对比「保存前的旧数据」与「本次保存提交的新数据」，生成 remark 对象（JSON 树）：
 *
 * <ul>
 *   <li>无审批链：{@code {"change":[...]}}
 *   <li>有审批链：{@code {"comment":"审批原因","change":[...]}}（comment 为空时退化为无审批链结构）
 * </ul>
 *
 * <p>字段明细会补充 {@code cloud} 标记（1-来自词云，0-否）。无任何变更时返回 {@code null}，调用方据此跳过日志记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperateLogRemarkUtil {

    /** remark 中字段明细数组的键名 */
    private static final String COLUMNS_KEY = "columns";

    /** 字段明细中的字段英文名键名（表名.列名） */
    private static final String FIELD_KEY = "field";

    /** 字段明细中的词云标记键名 */
    private static final String CLOUD_KEY = "cloud";

    /** 字段明细中的旧值键名（原始类型，不再向下递归） */
    private static final String OLD_KEY = "old";

    /** 字段明细中的新值键名（原始类型，不再向下递归） */
    private static final String NEWER_KEY = "newer";

    /** 词云标记：来自词云 */
    private static final Integer CLOUD_YES = 1;

    /** 词云标记：非词云 */
    private static final Integer CLOUD_NO = 0;

    private final OperateLogRemarkConverter operateLogRemarkConverter;

    private final ObjectMapper objectMapper;

    private final ConfigApi configApi;

    /**
     * 生成 remark 对象（无审批链）：{@code {"change":[...]}}。
     *
     * @param oldData 保存前的旧数据（纯新增时为 null）
     * @param newData 本次保存提交的数据
     * @param moduleCacheMap 模块元数据（含字段、唯一标识、子表、模块名）
     * @param menuId 菜单ID（取变更项名称，可为 null）
     * @return remark 对象；无任何变更时返回 null
     */
    public JsonNode buildRemark(
            NestedModuleDataDTO oldData,
            NestedModuleDataDTO newData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long menuId) {
        return buildRemark(oldData, newData, moduleCacheMap, menuId, null);
    }

    /**
     * 生成 remark 对象（有审批链）：{@code {"comment":"审批原因","change":[...]}}。
     *
     * @param oldData 保存前的旧数据（纯新增时为 null）
     * @param newData 本次保存提交的数据
     * @param moduleCacheMap 模块元数据（含字段、唯一标识、子表、模块名）
     * @param menuId 菜单ID（取变更项名称，可为 null）
     * @param comment 审批原因；为空时退化为无审批链结构 {@code {"change":[...]}}
     * @return remark 对象；无任何变更时返回 null
     */
    public JsonNode buildRemark(
            NestedModuleDataDTO oldData,
            NestedModuleDataDTO newData,
            Map<Long, SysModuleCombineInfo> moduleCacheMap,
            Long menuId,
            String comment) {
        OperateLogRemarkDTO remark =
                operateLogRemarkConverter.convert(oldData, newData, moduleCacheMap, menuId);
        if (remark == null || CollectionUtil.isEmpty(remark.getChange())) {
            return null;
        }
        if (StringUtils.isNotBlank(comment)) {
            remark.setComment(comment);
        }
        JsonNode remarkNode;
        try {
            remarkNode = objectMapper.valueToTree(remark);
        } catch (Exception e) {
            log.error("操作日志 remark 转换失败", e);
            throw new BusinessException("操作日志生成失败：remark 格式不正确");
        }
        // 字段明细补充词云标记：sys_field.relation_business_no 有值视为来自词云
        fillCloudFlags(remarkNode);
        return remarkNode;
    }

    /** 给 remark 中所有 columns 明细补充 cloud 标记（1-来自词云，0-否） */
    private void fillCloudFlags(JsonNode node) {
        fillCloudFlags(node, loadCloudFieldIndexes());
    }

    private void fillCloudFlags(JsonNode node, Set<String> cloudFieldIndexes) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode columns = object.get(COLUMNS_KEY);
            if (columns != null && columns.isArray()) {
                for (JsonNode element : columns) {
                    if (!(element instanceof ObjectNode column)) {
                        continue;
                    }
                    JsonNode field = column.get(FIELD_KEY);
                    boolean cloud =
                            field != null
                                    && field.isTextual()
                                    && cloudFieldIndexes.contains(field.asText());
                    column.put(CLOUD_KEY, cloud ? CLOUD_YES : CLOUD_NO);
                }
            }
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                // old/newer 为字段值原文（可能是数组/对象），不做词云标记的递归
                if (OLD_KEY.equals(name) || NEWER_KEY.equals(name)) {
                    continue;
                }
                fillCloudFlags(object.get(name), cloudFieldIndexes);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                fillCloudFlags(element, cloudFieldIndexes);
            }
        }
    }

    /** 查询配置中心词云字段索引（表名.列名）；查询失败按无词云字段处理，不阻断日志记录 */
    private Set<String> loadCloudFieldIndexes() {
        // 审批回调运行在审批引擎事务内，事务连接已绑定业务库，@DataSource 切换失效；
        // 配置中心查询需换到事务外线程执行，否则会命中业务库连接导致查不到 sys_field
        // （与 ModuleMetaDataServiceImpl 的 getModuleMetaDataMapAsync 同一思路）
        String projectNo = AppContext.getProjectNo();
        Long subjectId = AppContext.getSubjectId();
        try {
            List<FieldWordCloudDTO> fields =
                    CompletableFuture.supplyAsync(
                                    () -> configApi.getWordCloudFields(projectNo, subjectId))
                            .get();
            Set<String> indexes = new HashSet<>();
            for (FieldWordCloudDTO field : fields) {
                if (StringUtils.isNotBlank(field.getRelationBusinessNo())
                        && field.getTableName() != null
                        && field.getColumnName() != null) {
                    indexes.add(field.getTableName() + "." + field.getColumnName());
                }
            }
            return indexes;
        } catch (Exception e) {
            log.warn("操作日志词云字段查询失败，全部标记为非词云", e);
            return Collections.emptySet();
        }
    }
}
