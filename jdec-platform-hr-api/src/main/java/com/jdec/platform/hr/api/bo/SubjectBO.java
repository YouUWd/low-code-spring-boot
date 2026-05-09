package com.jdec.platform.hr.api.bo;

import java.io.Serializable;
import lombok.Data;

@Data
public class SubjectBO implements Serializable {
    private Long id;

    /** 主体名称 */
    private String subjectName;

    /** 对应公司名称 */
    private String companyName;

    /** 别名 */
    private String alias;
}
