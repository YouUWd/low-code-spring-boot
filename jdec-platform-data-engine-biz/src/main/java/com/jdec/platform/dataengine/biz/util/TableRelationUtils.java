package com.jdec.platform.dataengine.biz.util;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.TabTreeNodeDTO;
import com.jdec.platform.dataengine.biz.dto.TableRelationDTO;
import java.util.LinkedList;
import org.apache.commons.lang3.StringUtils;

public class TableRelationUtils {

    public static TableRelationDTO getTableRelationBySubTable(
            TabTreeNodeDTO root, String subTable) {
        if (root == null || StringUtils.isEmpty(subTable)) {
            return null;
        }
        LinkedList<TabTreeNodeDTO> qp = new LinkedList<>();
        qp.add(root);

        while (!qp.isEmpty()) {
            TabTreeNodeDTO current = qp.poll();
            if (StringUtils.equals(current.getNode().getTableName(), subTable)) {
                return TableRelationDTO.builder()
                        .mainTable(root.getNode().getParentTableName())
                        .mainField(root.getNode().getParentTableField())
                        .joinTable(root.getNode().getTableName())
                        .joinField(root.getNode().getParentTableField())
                        .relationType(root.getNode().getJoinType())
                        .build();
            }
            if (CollectionUtil.isNotEmpty(current.getChildren())) {
                current.getChildren().stream().forEach(qp::add);
            }
        }
        return null;
    }
}
