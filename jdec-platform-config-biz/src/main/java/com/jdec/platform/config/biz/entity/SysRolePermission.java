package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色权限关联 Entity
 *
 * <p>对应数据库表: sys_role_permission
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("sys_role_permission")
public class SysRolePermission {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色 ID */
    private Long roleId;

    /** 权限 ID */
    private Long permissionId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
