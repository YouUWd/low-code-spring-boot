package com.jdec.platform.auth.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jdec.platform.shared.model.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 系统用户实体。 */
@TableName("sys_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysUser extends BaseEntity {
    private String userName;
    private String password;
    private String email;
    private String phone;
    @Builder.Default private Integer status = 1;
}
