package com.jdec.platform.hr.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户基本信息响应 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBaseResp {

    @Schema(description = "ID", example = "1")
    private Long id;

    @Schema(description = "用户ID", example = "1")
    private Integer userId;

    @Schema(description = "用户名", example = "张三")
    private String userName;

    @Schema(description = "用户简称", example = "ZS")
    private String shortName;

    @Schema(description = "性别", example = "1")
    private Integer sex;

    @Schema(description = "职务类别", example = "1")
    private Integer jobClass;

    @Schema(description = "职位ID", example = "10")
    private Integer positionId;

    @Schema(description = "职级", example = "5")
    private Integer jobLevel;

    @Schema(description = "职等", example = "3")
    private Integer jobGrade;

    @Schema(description = "入职日期", example = "20200101")
    private Integer enterDate;

    @Schema(description = "离职日期", example = "20250101")
    private Integer resignationDate;

    @Schema(description = "身份证号", example = "110101199001011234")
    private String userIDCard;

    @Schema(description = "出生日期", example = "1990-01-01")
    private String birthday;

    @Schema(description = "婚姻状态", example = "1")
    private Integer marital;

    @Schema(description = "地址", example = "北京市朝阳区")
    private String address;

    @Schema(description = "Hello", example = "欢迎光临")
    private HelloResp helloResp;
}
