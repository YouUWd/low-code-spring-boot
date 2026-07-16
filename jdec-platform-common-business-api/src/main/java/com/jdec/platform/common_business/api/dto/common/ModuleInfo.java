package com.jdec.platform.common_business.api.dto.common;

import com.jdec.platform.common_business.api.dto.response.SubjectResp;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 系统设置模块配置数据类 * */
@Data
@Builder
public class ModuleInfo {
    private Long moduleId;

    private String moduleSlug;

    private String primaryTable;

    private String moduleType;

    private List<TableHeaderConf> tableHeader;

    private List<SubjectResp> sourceSubjects;

    // 主表外键关联字段 用于详情页的关联表查询
    private String relateSearchField;
}
