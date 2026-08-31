package com.jdec.platform.data.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单张物理表的字段权限白名单")
public class TablePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "可查阅展示的字段列表")
    private List<String> view;

    @Schema(description = "可申请录入的字段列表")
    private List<String> apply;

    @Schema(description = "可变更修改的字段列表")
    private List<String> edit;
}
