package com.jdec.platform.dataengine.api.dto.response;

import com.jdec.platform.dataengine.api.dto.model.UserInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "登记人信息返回数据模型")
public class CreatedUserInfoResp {

    @Schema(description = "登记时间")
    private LocalDateTime createdDate;

    @Schema(description = "登记人信息")
    private UserInfo createUser;
}
