package com.jdec.platform.dataengine.biz.util;

import cn.hutool.core.collection.CollectionUtil;
import com.jdec.platform.dataengine.api.dto.model.TabNodeDTO;
import com.jdec.platform.dataengine.api.dto.model.TabTreeNodeDTO;
import com.jdec.platform.dataengine.biz.dto.TableRelationDTO;
import java.util.ArrayList;
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

    public static TabTreeNodeDTO getRootTabTreeNode() {
        TabNodeDTO node1 = TabNodeDTO.builder().tableName("info").build();

        TabTreeNodeDTO root1 =
                TabTreeNodeDTO.builder().node(node1).children(new ArrayList<>()).build();

        TabNodeDTO node2 =
                TabNodeDTO.builder()
                        .tableName("basic_info")
                        .parentTableName("info")
                        .joinType("1:1")
                        .parentTableField("id")
                        .subTableField("company_id")
                        .build();

        TabTreeNodeDTO root2 =
                TabTreeNodeDTO.builder().node(node2).children(new ArrayList<>()).build();
        root1.getChildren().add(root2);

        TabNodeDTO node3 =
                TabNodeDTO.builder()
                        .tableName("member")
                        .parentTableName("info")
                        .joinType("1:N")
                        .parentTableField("id")
                        .subTableField("company_id")
                        .build();

        TabTreeNodeDTO root3 =
                TabTreeNodeDTO.builder().node(node3).children(new ArrayList<>()).build();

        root1.getChildren().add(root3);

        TabNodeDTO node4 =
                TabNodeDTO.builder()
                        .tableName("address")
                        .parentTableName("info")
                        .joinType("1:N")
                        .parentTableField("id")
                        .subTableField("company_id")
                        .build();

        TabTreeNodeDTO root4 =
                TabTreeNodeDTO.builder().node(node4).children(new ArrayList<>()).build();

        root1.getChildren().add(root4);

        TabNodeDTO node5 =
                TabNodeDTO.builder()
                        .tableName("financial_account")
                        .parentTableName("info")
                        .joinType("1:N")
                        .parentTableField("id")
                        .subTableField("company_id")
                        .build();

        TabTreeNodeDTO root5 =
                TabTreeNodeDTO.builder().node(node5).children(new ArrayList<>()).build();

        root1.getChildren().add(root5);

        TabNodeDTO node6 =
                TabNodeDTO.builder()
                        .tableName("credit_rating")
                        .parentTableName("info")
                        .joinType("1:N")
                        .parentTableField("id")
                        .subTableField("company_id")
                        .build();

        TabTreeNodeDTO root6 =
                TabTreeNodeDTO.builder().node(node6).children(new ArrayList<>()).build();

        root1.getChildren().add(root6);

        TabNodeDTO node7 =
                TabNodeDTO.builder()
                        .tableName("member_contact")
                        .parentTableName("member")
                        .joinType("1:N")
                        .parentTableField("id")
                        .subTableField("member_id")
                        .build();

        TabTreeNodeDTO root7 =
                TabTreeNodeDTO.builder().node(node7).children(new ArrayList<>()).build();

        root3.getChildren().add(root7);

        return root1;
    }
}
