package com.jdec.platform.dataengine.biz.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class SubjectResp implements Serializable {

    private Integer id;

    private String subjectName;

    private String companyName;

    private String alias;

    private List<RoleResp> roleRespList;
}
