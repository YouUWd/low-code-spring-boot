package com.jdec.platform.common_business.biz.log.impl;

import com.jdec.platform.common_business.biz.enums.BizMsgEnum;
import com.jdec.platform.common_business.biz.log.BizMsg;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class ApprovalBizMsg implements BizMsg {

    private final Set<BizMsgEnum> referSets = Set.of(BizMsgEnum.APPROVAL_CHAIN);

    @Override
    public Boolean referToBizMsg(BizMsgEnum msgEnum) {
        return referSets.contains(msgEnum);
    }

    @Override
    public Boolean processBizMsg(BizMsgEnum msgEnum, Object msg) {
        return null;
    }
}
