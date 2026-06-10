package com.jdec.platform.config.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存角色及用户关联请求
 *
 * <p>用于保存角色信息及其用户关联，id为空则新增角色，不为空则修改
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "保存角色及用户关联请求")
public class SaveRoleUserReq {

    @Schema(description = "角色ID，为空则新增", example = "19")
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称", example = "这就不好说了")
    private String roleName;

    @Schema(description = "内部用户列表")
    private List<RoleUserReq> userInsideList;

    @Schema(description = "外部用户列表")
    private List<RoleUserReq> userExternalList;

    @Schema(description = "主体列表")
    private List<RoleSubjectReq> subjectList;

    @Schema(description = "是否强制覆盖", example = "false")
    private Boolean force;

    /** 获取所有内部用户ID列表 (字符串格式) */
    @Schema(hidden = true)
    public List<String> getUserInsideIds() {
        if (userInsideList == null) {
            return null;
        }
        return userInsideList.stream().map(RoleUserReq::getUserId).collect(Collectors.toList());
    }

    /** 获取所有外部用户ID列表 (字符串格式) */
    @Schema(hidden = true)
    public List<String> getUserExternalIds() {
        if (userExternalList == null) {
            return null;
        }
        return userExternalList.stream().map(RoleUserReq::getUserId).collect(Collectors.toList());
    }

    /** 获取所有主体ID列表 */
    @Schema(hidden = true)
    public List<Long> getSubjectIds() {
        if (subjectList == null) {
            return null;
        }
        return subjectList.stream().map(RoleSubjectReq::getSubjectId).collect(Collectors.toList());
    }

    /** 辅助方法：获取主体列表的别名，兼容旧代码 */
    @Schema(hidden = true)
    public List<RoleSubjectReq> getSubjects() {
        return subjectList;
    }

    /** 获取所有用户列表（内部+外部） */
    @Schema(hidden = true)
    public List<RoleUserReq> getUsers() {
        List<RoleUserReq> allUsers = new java.util.ArrayList<>();
        if (userInsideList != null) {
            allUsers.addAll(userInsideList);
        }
        if (userExternalList != null) {
            allUsers.addAll(userExternalList);
        }
        return allUsers;
    }
}
