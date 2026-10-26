package com.jdec.platform.data.api.model.predicate;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 复合逻辑谓词（CompoundPredicate）。
 *
 * <p>表示纯逻辑括号容器（AND / OR / NOT），其内部包含一个或多个子谓词。 彻底消除“叶子节点自带逻辑”、“结合律退化”与“同级括号混杂”的二义性。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "复合逻辑谓词（纯逻辑容器，用于 AND / OR / NOT 分组）")
public class CompoundPredicate implements Predicate {

    @Schema(description = "逻辑操作符: AND, OR, NOT", example = "OR")
    private String operator;

    @Schema(description = "子谓词列表")
    @Builder.Default
    private List<Predicate> predicates = new ArrayList<>();

    @Override
    public String getType() {
        return "compound";
    }

    public static CompoundPredicate and(List<Predicate> predicates) {
        return CompoundPredicate.builder().operator("AND").predicates(predicates).build();
    }

    public static CompoundPredicate and(Predicate... predicates) {
        return and(new ArrayList<>(Arrays.asList(predicates)));
    }

    public static CompoundPredicate or(List<Predicate> predicates) {
        return CompoundPredicate.builder().operator("OR").predicates(predicates).build();
    }

    public static CompoundPredicate or(Predicate... predicates) {
        return or(new ArrayList<>(Arrays.asList(predicates)));
    }

    public static CompoundPredicate not(Predicate predicate) {
        return CompoundPredicate.builder()
                .operator("NOT")
                .predicates(new ArrayList<>(List.of(predicate)))
                .build();
    }
}
