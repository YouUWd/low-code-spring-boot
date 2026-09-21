package com.jdec.platform.auth.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 切换角色响应（迁移自 student-manage 的 SelectRoleResp，字段与源契约保持一致） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "切换角色响应")
public class SelectRoleResp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前用户有角色的主体列表")
    private List<SubjectResp> subjectList;

    @Schema(description = "当前角色可见的菜单树")
    private List<MenuResp> menuList;

    @Schema(description = "切换后的主体ID")
    private Long subjectId;

    @Schema(description = "切换后的角色ID")
    private Long roleId;

    @Schema(description = "切换后的菜单ID")
    private Long menuId;

    @Schema(description = "当前登录用户是否具备模拟登录权限")
    private Boolean simulateLogin;
}
