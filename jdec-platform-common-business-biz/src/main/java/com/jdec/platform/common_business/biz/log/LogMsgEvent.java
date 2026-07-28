package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.enums.BizLogEnum;
import com.jdec.platform.common_business.biz.enums.BizMsgEnum;
import java.io.Serializable;
import lombok.Data;

@Data
public class LogMsgEvent implements Serializable {

    private BizLogEnum bizLogEnum;

    private BizMsgEnum bizMsgEnum;

    private Object target;
}
