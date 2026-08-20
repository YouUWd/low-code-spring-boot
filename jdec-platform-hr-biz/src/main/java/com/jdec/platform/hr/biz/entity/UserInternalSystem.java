package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 内部系统 */
@TableName("user_internal_system")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInternalSystem extends BaseHrEntity {
    private Integer userId;
    private String systemName;
    private String icon;
    private String url;
}
