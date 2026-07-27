package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.enums.BizMsgEnum;

public interface BizMsg {

    public Boolean referToBizMsg(BizMsgEnum msgEnum);

    public Boolean processBizMsg(BizMsgEnum msgEnum, Object msg);
}
