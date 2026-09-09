package com.jdec.platform.dataengine.biz.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 平铺的菜单列表返回数据
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuListResp {
    private Long id;

    private Long pid;

    private String title;

    private String param;

    private Integer menuType;

    private Integer category;

    private String imgDefault;

    private String imgActive;

    private Long moduleId;

    private Integer sortOrder;

    private Integer showed;

    private String docUrl;

    private String docName;

    private String videoUrl;

    private String videoName;
}
