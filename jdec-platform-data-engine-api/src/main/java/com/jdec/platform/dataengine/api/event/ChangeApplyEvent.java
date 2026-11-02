package com.jdec.platform.dataengine.api.event;

import com.jdec.platform.config.api.dto.common.ModuleInfo;
import com.jdec.platform.shared.security.model.LoginUser;
import java.util.Map;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

@Data
public class ChangeApplyEvent extends ApplicationEvent {
    public ChangeApplyEvent(Object source) {
        super(source);
    }

    private String projectNo;

    private Long subjectId;

    private LoginUser loginUser;

    private ModuleInfo rootModuleInfo;

    private Map<String, Object> bizMap;
}
