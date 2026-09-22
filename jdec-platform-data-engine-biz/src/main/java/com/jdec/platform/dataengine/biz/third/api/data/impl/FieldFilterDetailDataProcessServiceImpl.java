package com.jdec.platform.dataengine.biz.third.api.data.impl;

import com.jdec.platform.dataengine.api.dto.enums.QueryType;
import com.jdec.platform.dataengine.api.dto.model.ModuleFieldDTO;
import com.jdec.platform.dataengine.api.dto.model.NestedModuleDataDTO;
import com.jdec.platform.dataengine.api.dto.model.RecordDataDTO;
import com.jdec.platform.dataengine.biz.dto.context.DataIntegrationContext;
import com.jdec.platform.dataengine.biz.third.api.data.DetailDataProcessService;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 字段权限 元数据 */
@Service
@RequiredArgsConstructor
public class FieldFilterDetailDataProcessServiceImpl implements DetailDataProcessService {
    private final Set<QueryType> TYPE_SET = Set.of(QueryType.DETAIL_APPROVAL_HISTORY);

    @Override
    public Boolean isReferTo(QueryType queryType) {
        return TYPE_SET.contains(queryType);
    }

    @Override
    public void processAssembleData(
            List<NestedModuleDataDTO> records, DataIntegrationContext context) {
        filterInPlace(records, context.getModuleIdToFieldMap());
    }

    /**
     * 原地递归过滤模块树
     *
     * @param moduleNodeList 待处理ModuleNode列表（直接原地修改）
     * @param moduleIdToFieldMap 模块id及模块显示字段映射map
     */
    public static void filterInPlace(
            List<NestedModuleDataDTO> moduleNodeList,
            Map<Long, List<ModuleFieldDTO>> moduleIdToFieldMap) {
        if (moduleNodeList == null || moduleNodeList.isEmpty()) {
            return;
        }

        // Iterator安全删除：过滤moduleNode，不符合moduleId直接移除
        Iterator<NestedModuleDataDTO> moduleIt = moduleNodeList.iterator();
        while (moduleIt.hasNext()) {
            NestedModuleDataDTO moduleNode = moduleIt.next();
            Long moduleId = moduleNode.getModuleId();
            // moduleId不满足条件 → 删除当前moduleNode
            if (!moduleIdToFieldMap.containsKey(moduleId)) {
                moduleIt.remove();
                continue;
            }

            // moduleId符合，继续处理内部record列表
            List<RecordDataDTO> recordList = moduleNode.getRecord();
            if (recordList == null || recordList.isEmpty()) {
                continue;
            }

            Set<String> tableFields =
                    moduleIdToFieldMap.get(moduleId).stream()
                            .map(ele -> ele.getTableName() + "." + ele.getColumnName())
                            .collect(Collectors.toSet());

            for (RecordDataDTO recordNode : recordList) {
                // 1. 过滤row里面的字段key，原地删除不满足key
                Map<String, Object> rowMap = recordNode.getRow();
                if (rowMap != null) {
                    Iterator<Map.Entry<String, Object>> rowIt = rowMap.entrySet().iterator();
                    while (rowIt.hasNext()) {
                        Map.Entry<String, Object> entry = rowIt.next();
                        String key = entry.getKey();
                        if (!tableFields.contains(key)) {
                            rowIt.remove();
                        }
                    }
                }

                // 2. 递归处理当前record下的children（子ModuleNode列表）
                List<NestedModuleDataDTO> children = recordNode.getChildren();
                filterInPlace(children, moduleIdToFieldMap);
            }
        }
    }
}
