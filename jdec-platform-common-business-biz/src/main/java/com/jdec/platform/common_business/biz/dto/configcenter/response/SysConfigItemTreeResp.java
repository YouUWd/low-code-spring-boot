package com.jdec.platform.common_business.biz.dto.configcenter.response;

import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class SysConfigItemTreeResp implements Serializable {

    private Long id;

    private String categoryAlias; // 冗余分类alias

    private Long pid;

    private String path; // 	层级路径

    private Integer depth; // 层级深度

    private String label; // 显示名称

    private String value; // 存储值

    private String description; // 说明

    private String extra; // 扩展属性

    private Integer source; // 1系统内置 2用户自定义

    private Integer sort; // 	排序

    private Integer status; //  1启用 0禁用

    private String projectNo; // 应用编码

    private Long subjectId; // 主体ID

    private List<SysConfigItemTreeResp> children; // 	子节点
}
