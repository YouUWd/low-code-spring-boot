/** 外部对象业务模块 (External Object BIZ) */
@ApplicationModule(
        displayName = "External Object BIZ",
        allowedDependencies = {
            "externalobject.api",
            "common.api",
            "config.api",
            "config.open",
            "dataengine.api",
            "validation",
            "shared"
        })
package com.jdec.platform.externalobject.biz;

import org.springframework.modulith.ApplicationModule;
