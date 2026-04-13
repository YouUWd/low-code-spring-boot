package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 组织架构版本记录 */
@TableName("organization_version_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationVersionRecord extends BaseHrEntity {
    private String businessNo;
    private Integer organizationId;
    private Integer projectId;
    private Integer pid;
    private String type;
    private String title;
    private String extraProperty;
}
