package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import java.io.Serializable;
import lombok.Data;

@Data
public class LogEvent implements Serializable {

    private BizLogEnum bizLogEnum;

    private Object target;
}
