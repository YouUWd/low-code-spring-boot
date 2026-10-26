/** 数据引擎业务模块 (Data Engine BIZ) */
@ApplicationModule(
        displayName = "Data Engine BIZ",
        allowedDependencies = {
            "dataengine.api",
            "common.api",
            "config.api",
            "config.open",
            "auth.api",
            "approval.api",
            "shared"
        })
package com.jdec.platform.dataengine.biz;

import org.springframework.modulith.ApplicationModule;
