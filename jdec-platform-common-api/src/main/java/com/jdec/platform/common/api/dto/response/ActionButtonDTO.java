package com.jdec.platform.common.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通用操作按钮定义模型 (面向对象强类型定义) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "通用操作按钮模型")
public class ActionButtonDTO implements Serializable {

    @Schema(description = "按钮标识编码 (如 add, handle, view, enable_company 等)")
    private String buttonKey;

    @Schema(description = "按钮展示名称 (如 新增, 办理, 查阅, 启用 等)")
    private String buttonName;

    @Schema(description = "交互权限类型: 1-申请, 2-编辑, 3-查看 (可选)")
    private Integer type;

    @Schema(description = "按钮排序号 (可选)")
    private Integer sort;

    public static ActionButtonDTO of(String buttonKey, String buttonName) {
        return ActionButtonDTO.builder().buttonKey(buttonKey).buttonName(buttonName).build();
    }

    public static ActionButtonDTO of(
            String buttonKey, String buttonName, Integer type, Integer sort) {
        return ActionButtonDTO.builder()
                .buttonKey(buttonKey)
                .buttonName(buttonName)
                .type(type)
                .sort(sort)
                .build();
    }
}
