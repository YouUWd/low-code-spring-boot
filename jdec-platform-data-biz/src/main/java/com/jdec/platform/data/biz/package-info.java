/** 数据引擎业务模块 (Data BIZ) */
@ApplicationModule(
        displayName = "Data BIZ",
        allowedDependencies = {
            "data.api",
            "config.api",
            "config.open",
            "auth.api",
            "approval.api",
            "shared"
        })
package com.jdec.platform.data.biz;

import org.springframework.modulith.ApplicationModule;
