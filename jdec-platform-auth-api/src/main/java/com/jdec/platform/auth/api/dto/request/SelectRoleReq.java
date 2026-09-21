package com.jdec.platform.auth.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/** 切换角色请求（迁移自 student-manage 的 SelectRoleReq） */
@Data
@Schema(description = "切换角色请求")
public class SelectRoleReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色ID，为空时由服务端按当前登录态补齐")
    private Long roleId;

    @Schema(description = "主体ID，为空时由服务端按当前登录态补齐")
    private Long subjectId;

    @Schema(description = "菜单ID，为空时回退到菜单树的第一个根节点")
    private Long menuId;
}
