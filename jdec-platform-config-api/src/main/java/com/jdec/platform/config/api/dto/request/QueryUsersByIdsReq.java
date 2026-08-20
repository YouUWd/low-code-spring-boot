package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/** 根据用户ID列表批量查询用户请求 */
@Data
@Schema(description = "根据用户ID列表批量查询用户请求")
public class QueryUsersByIdsReq {

    @NotEmpty(message = "用户ID列表不能为空")
    @Schema(description = "用户ID列表（sys_user.user_id）", example = "[1, 2, 3]")
    private List<Long> userIds;
}
