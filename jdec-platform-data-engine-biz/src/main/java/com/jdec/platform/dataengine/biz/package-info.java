/** 数据引擎业务模块 (Data Engine BIZ) */
@ApplicationModule(
        displayName = "Data Engine BIZ",
        allowedDependencies = {
            "dataengine.api",
            "config.api",
            "auth.api",
            "approval.api",
            "shared"
        })
package com.jdec.platform.dataengine.biz;

import org.springframework.modulith.ApplicationModule;
