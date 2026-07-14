package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import com.jdec.platform.common_business.api.dto.common.TableHeaderConf;
import com.jdec.platform.common_business.api.dto.response.SubjectResp;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleResp implements Serializable {

    private Integer id;

    private String projectNo;

    private Integer subjectId;

    private String moduleCode;

    private String moduleName;

    private String moduleDesc;

    private Integer parentId;

    private Integer moduleLevel;

    private String modulePath;

    private String primaryTable;

    private String moduleType;

    private Integer approvalRequired;

    private String moduleStatus;

    private Integer leafFlag;

    private Integer sortOrder;

    private List<TableHeaderConf> tableHeader;

    private List<SubjectResp> sourceSubjects;

    private Integer createdBy;

    private String createdDate;

    private String createdName;

    private Integer updatedBy;

    private String updatedDate;

    private String updatedName;

    // 主表外键关联字段
    private String relateSearchField;

    private Long detailModuleId;
}
