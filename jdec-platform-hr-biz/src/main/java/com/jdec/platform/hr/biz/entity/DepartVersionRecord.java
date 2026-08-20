package com.jdec.platform.hr.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 部门管理版本记录 */
@TableName("depart_version_record")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartVersionRecord extends BaseHrEntity {
    private String businessNo;
    private Integer departManageId;
    private Integer projectId;
    private Integer pid;
    private String type;
    private String departNumber;
    private String title;
    private Integer childNum;
    private Integer sortOrder;
    private Integer expanded;
    private Integer dragged;
    private String manager;
    private Integer isDelete;
}
