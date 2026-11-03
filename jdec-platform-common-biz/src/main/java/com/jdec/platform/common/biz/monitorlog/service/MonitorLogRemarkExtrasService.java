package com.jdec.platform.common.biz.monitorlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdec.platform.config.api.dto.common.FieldColumnExtrasDTO;
import com.jdec.platform.config.open.api.ConfigApi;
import com.jdec.platform.shared.context.AppContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 操作日志 remark 字段扩展信息补充服务。
 *
 * <p>查询日志时按当前 {@code sys_field} 配置为 remark 中的字段明细实时补充（覆盖）：
 *
 * <ul>
 *   <li>{@code cloud}：1-字段配置了词云关联（{@code relation_business_no} 非空），0-否；始终输出；
 *   <li>{@code businessTypeCode}：业务类型编码（{@code business_type_code}），未配置时不输出。
 * </ul>
 *
 * <p>历史日志在生成时未记录这两项，查询时统一补齐，前端无需区分新旧数据；配置查询失败时保持 remark 原样，不破坏已落库的扩展信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorLogRemarkExtrasService {

    /** 字段明细所在键名 */
    private static final String COLUMNS_KEY = "columns";

    /** 主模块字段变更平铺数组键名（元素为字段明细对象，非 columns 包裹） */
    private static final String MAIN_KEY = "main";

    /** 字段明细中的字段英文名键名（表名.列名） */
    private static final String FIELD_KEY = "field";

    /** 字段明细中的词云标记键名 */
    private static final String CLOUD_KEY = "cloud";

    /** 字段明细中的业务类型编码键名 */
    private static final String BUSINESS_TYPE_CODE_KEY = "businessTypeCode";

    /** 字段明细中的旧值键名（字段值原文，不做递归） */
    private static final String OLD_KEY = "old";

    /** 字段明细中的新值键名（字段值原文，不做递归） */
    private static final String NEWER_KEY = "newer";

    /** 词云标记：来自词云 */
    private static final Integer CLOUD_YES = 1;

    /** 词云标记：非词云 */
    private static final Integer CLOUD_NO = 0;

    private final ConfigApi configApi;

    private final ObjectMapper objectMapper;

    /**
     * 给 remark 中所有字段明细补充 cloud 与 businessTypeCode。
     *
     * @param remark 日志备注（反序列化后的对象/数组结构）
     * @return 补充后的备注对象；remark 为空或配置查询失败时原样返回
     */
    public Object fillColumnExtras(Object remark) {
        if (remark == null) {
            return null;
        }
        FieldColumnExtras extras = loadFieldColumnExtras();
        // 配置查询失败时保持原样，避免把已落库的扩展信息覆盖成默认值
        if (extras == null) {
            return remark;
        }
        JsonNode root = objectMapper.valueToTree(remark);
        fillColumnExtras(root, extras);
        return objectMapper.convertValue(root, Object.class);
    }

    /** 查询配置中心字段扩展配置（词云标记 + 业务类型编码）；失败返回 null */
    private FieldColumnExtras loadFieldColumnExtras() {
        try {
            List<FieldColumnExtrasDTO> fields =
                    configApi.getFieldColumnExtras(
                            AppContext.getProjectNo(), AppContext.getSubjectId());
            if (fields == null || fields.isEmpty()) {
                return FieldColumnExtras.EMPTY;
            }
            Set<String> cloudFieldIndexes = new HashSet<>();
            Map<String, String> businessTypeCodes = new HashMap<>();
            for (FieldColumnExtrasDTO field : fields) {
                if (field.getTableName() == null || field.getColumnName() == null) {
                    continue;
                }
                String fieldIndex = field.getTableName() + "." + field.getColumnName();
                if (StringUtils.isNotBlank(field.getRelationBusinessNo())) {
                    cloudFieldIndexes.add(fieldIndex);
                }
                if (StringUtils.isNotBlank(field.getBusinessTypeCode())) {
                    businessTypeCodes.put(fieldIndex, field.getBusinessTypeCode());
                }
            }
            return new FieldColumnExtras(cloudFieldIndexes, businessTypeCodes);
        } catch (Exception e) {
            log.warn("操作日志字段扩展配置查询失败，remark 保持原样返回", e);
            return null;
        }
    }

    /** 递归遍历 remark，给每个字段明细（columns 或 main 数组中的元素）补充 cloud 与 businessTypeCode */
    private void fillColumnExtras(JsonNode node, FieldColumnExtras extras) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode columns = object.get(COLUMNS_KEY);
            if (columns != null && columns.isArray()) {
                for (JsonNode element : columns) {
                    fillColumnExtrasColumn(element, extras);
                }
            }
            // 主模块字段变更（main 为字段明细对象数组，非 columns 包裹）
            JsonNode main = object.get(MAIN_KEY);
            if (main != null && main.isArray()) {
                for (JsonNode element : main) {
                    fillColumnExtrasColumn(element, extras);
                }
            }
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                // old/newer 为字段值原文（可能是数组/对象），不做字段扩展信息的递归；
                // columns/main 已单独处理，不再递归
                if (OLD_KEY.equals(name)
                        || NEWER_KEY.equals(name)
                        || COLUMNS_KEY.equals(name)
                        || MAIN_KEY.equals(name)) {
                    continue;
                }
                fillColumnExtras(object.get(name), extras);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                fillColumnExtras(element, extras);
            }
        }
    }

    /** 给单个字段明细对象补充 cloud 与 businessTypeCode */
    private void fillColumnExtrasColumn(JsonNode node, FieldColumnExtras extras) {
        if (!(node instanceof ObjectNode column)) {
            return;
        }
        JsonNode field = column.get(FIELD_KEY);
        String fieldIndex = field != null && field.isTextual() ? field.asText() : null;
        boolean cloud = fieldIndex != null && extras.cloudFieldIndexes().contains(fieldIndex);
        column.put(CLOUD_KEY, cloud ? CLOUD_YES : CLOUD_NO);
        String businessTypeCode =
                fieldIndex != null ? extras.businessTypeCodes().get(fieldIndex) : null;
        // 与落库规则保持一致：有配置才输出，配置缺失时移除旧值
        if (StringUtils.isNotBlank(businessTypeCode)) {
            column.put(BUSINESS_TYPE_CODE_KEY, businessTypeCode);
        } else {
            column.remove(BUSINESS_TYPE_CODE_KEY);
        }
    }

    /**
     * 字段列扩展配置索引。
     *
     * @param cloudFieldIndexes 词云字段索引（表名.列名）
     * @param businessTypeCodes 业务类型编码映射（表名.列名 → businessTypeCode）
     */
    private record FieldColumnExtras(
            Set<String> cloudFieldIndexes, Map<String, String> businessTypeCodes) {

        private static final FieldColumnExtras EMPTY =
                new FieldColumnExtras(Collections.emptySet(), Collections.emptyMap());
    }
}
