package com.jdec.platform.common_business.biz.dto.configcenter.resp;

import com.jdec.platform.common_business.biz.dto.PersonalSettingColumnDTO;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class PersonalSettingResp implements Serializable {
    private Long id;

    private String moduleName;

    List<PersonalSettingColumnDTO> personalSettingColumns;
}
