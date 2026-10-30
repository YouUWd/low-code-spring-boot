package com.jdec.platform.common.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单变更申请启用状态与未读角标统计响应模型 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单变更申请启用状态与未读数量统计响应模型")
public class MenuChangeCountResp implements Serializable {

    @Schema(description = "是否启用变更申请: 1-是, 0-否", example = "1")
    private Integer enableChange;

    @Schema(description = "未读变更申请单数量", example = "5")
    private Long unreadCount;

    public static MenuChangeCountResp of(Integer enableChange, Long unreadCount) {
        return MenuChangeCountResp.builder()
                .enableChange(enableChange != null ? enableChange : 0)
                .unreadCount(unreadCount != null ? unreadCount : 0L)
                .build();
    }
}
