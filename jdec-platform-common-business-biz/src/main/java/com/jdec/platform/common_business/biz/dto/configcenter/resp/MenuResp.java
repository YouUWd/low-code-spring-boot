package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuResp implements Serializable {

    private Long menuId;

    private String menuTitle;

    private Long pid;

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

    private Integer level;

    private List<MenuResp> children;
}
