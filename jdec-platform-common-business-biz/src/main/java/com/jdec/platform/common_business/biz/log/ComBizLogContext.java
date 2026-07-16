package com.jdec.platform.common_business.biz.log;

import com.jdec.platform.common_business.biz.dto.BizLogDTO;

public final class ComBizLogContext {

    private static ThreadLocal<BizLogDTO> BIZ_LOG_CONTEXT = new ThreadLocal<>();

    public static void setBizLog(BizLogDTO dto) {
        BIZ_LOG_CONTEXT.set(dto);
    }

    public static BizLogDTO getBizLog() {
        return BIZ_LOG_CONTEXT.get();
    }

    public static void clear() {
        BIZ_LOG_CONTEXT.remove();
    }
}
