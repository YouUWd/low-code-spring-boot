package com.jdec.platform.config.biz.init;

import lombok.Data;

/** 项目主体初始化节点（对应 init/project_subject.json 中一项） */
@Data
public class ProjectSubjectInitNode {

    /** 主键ID，由初始化 JSON 指定并直接落库 */
    private Long id;

    /** 主体名称 */
    private String subjectName;

    /** 公司名称 */
    private String companyName;

    /** 别名 */
    private String alias;

    /** 主体ID，同时用于初始化时填充各业务表的 subject_id */
    private Long subjectId;

    /** 项目编码，同时用于初始化时填充各业务表的 project_no */
    private String projectNo;
}
