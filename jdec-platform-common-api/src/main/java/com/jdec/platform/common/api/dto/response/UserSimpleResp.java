package com.jdec.platform.common.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户简要信息响应 (提取核心要素: userId, 头像, 名字, 工号) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "用户简要信息响应")
public class UserSimpleResp implements Serializable {

    @Schema(description = "员工/用户ID", example = "1001")
    private Long userId;

    @Schema(description = "姓名/名称", example = "张三")
    private String userName;

    @Schema(description = "工号", example = "EMP001")
    private String workNumber;

    @Schema(description = "头像URL", example = "https://example.com/avatar.png")
    private String userAvatar;
}
