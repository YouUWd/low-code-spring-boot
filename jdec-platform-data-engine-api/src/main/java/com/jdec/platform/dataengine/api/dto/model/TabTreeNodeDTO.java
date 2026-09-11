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
}
