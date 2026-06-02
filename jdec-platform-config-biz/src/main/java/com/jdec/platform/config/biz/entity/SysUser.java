package com.jdec.platform.config.biz.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/** 用户表实体 */
@Data
@TableName("sys_user")
@Schema(description = "系统用户")
public class SysUser implements Serializable {

    @Schema(description = "自增ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "员工id(如果为内部用户不为0)")
    private Long userId;

    @Schema(description = "公司id(自动维护)")
    private Long companyId;

    @Schema(description = "主体id")
    private Long subjectId;

    @Schema(description = "部门ids(内部)")
    private String departIds;

    @Schema(description = "用户类型 1内部 2外部")
    private Integer userType;

    @Schema(description = "名称")
    private String userName;

    @Schema(description = "工号")
    private String workNumber;

    @Schema(description = "头像")
    private String userAvatar;

    @Schema(description = "手机")
    private String phone;

    @Schema(description = "对应企业微信简称")
    private String shortName;

    @Schema(description = "性别")
    private Integer sex;

    @Schema(description = "所属公司/内部人员为主体")
    private String companyName;

    @Schema(description = "部门名称")
    private String departName;

    @Schema(description = "部门领导人")
    private String leaderName;

    @Schema(description = "职位名称")
    private String positionName;

    @Schema(description = "主体简称")
    private String subjectName;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "状态 1启用2禁用")
    private Integer statusFlag;

    @Schema(description = "在职状态")
    private Integer employedStatus;

    @Schema(description = "是否超级人员 0-否 1-是")
    private Integer superFlag;

    @Schema(description = "逻辑删除标识 0-未删除 1-已删除")
    private Integer deleted;

    @Schema(description = "模拟操作用户")
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @Schema(description = "模拟操作用户名称")
    private String createdName;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdDate;

    @Schema(description = "更改人")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @Schema(description = "更改人名称")
    private String updatedName;

    @Schema(description = "更改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedDate;
}
