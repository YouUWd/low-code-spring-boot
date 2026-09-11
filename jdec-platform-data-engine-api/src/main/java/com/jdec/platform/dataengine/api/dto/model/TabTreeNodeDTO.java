package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表关联关系节点")
public class TabTreeNodeDTO implements Serializable {

    @Schema(description = "父节点")
    private TabNodeDTO node;

    @Schema(description = "子节点列表")
    private List<TabTreeNodeDTO> children;

    public static TabTreeNodeDTO buildRoot() {

        TabNodeDTO node1 = TabNodeDTO.builder().tableName("student").build();

        TabTreeNodeDTO root =
                TabTreeNodeDTO.builder().node(node1).children(new ArrayList<>()).build();

        TabNodeDTO node2 =
                TabNodeDTO.builder()
                        .tableName("course")
                        .parentTableName("student")
                        .joinType("1:N")
                        .parentTableField("id")
                        .subTableField("student_id")
                        .build();

        TabTreeNodeDTO root2 =
                TabTreeNodeDTO.builder().node(node2).children(new ArrayList<>()).build();
        root.getChildren().add(root2);

        TabNodeDTO node3 =
                TabNodeDTO.builder()
                        .tableName("score")
                        .parentTableName("student")
                        .joinType("1:N")
                        .parentTableField("id")
                        .subTableField("student_id")
                        .build();

        TabTreeNodeDTO root3 =
                TabTreeNodeDTO.builder().node(node3).children(new ArrayList<>()).build();

        root.getChildren().add(root3);

        TabNodeDTO node4 =
                TabNodeDTO.builder()
                        .tableName("score_detail")
                        .parentTableName("score")
                        .joinType("1:N")
                        .parentTableField("id")
                        .subTableField("student_score_id")
                        .build();

        TabTreeNodeDTO root4 =
                TabTreeNodeDTO.builder().node(node4).children(new ArrayList<>()).build();

        root3.getChildren().add(root4);

        return root;
    }

    /**
     * 森林查找：入参多个根节点List，找到第一个匹配节点值的节点，返回其整个子树
     *
     * @param root 森林：多个根节点组成的list
     * @param targetValue 目标节点值
     * @return 找到的子树根节点；找不到返回null
     */
    public static TabTreeNodeDTO findSubTreeByValue(TabTreeNodeDTO root, String targetValue) {
        if (root == null) {
            return null;
        }
        // 遍历森林中每一棵树
        TabTreeNodeDTO matchNode = dfsSearch(root, targetValue);
        if (matchNode != null) {
            return matchNode;
        }
        return null;
    }

    /** 单棵树内部DFS */
    private static TabTreeNodeDTO dfsSearch(TabTreeNodeDTO currNode, String targetValue) {
        if (currNode == null) {
            return null;
        }
        if (targetValue.equals(currNode.getNode().getTableName())) {
            return currNode;
        }
        // 递归遍历子节点
        for (TabTreeNodeDTO child : currNode.getChildren()) {
            TabTreeNodeDTO res = dfsSearch(child, targetValue);
            if (res != null) {
                return res;
            }
        }
        return null;
    }
}
