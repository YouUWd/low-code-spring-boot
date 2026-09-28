/** 数据引擎业务模块 (Data Engine BIZ) */
@org.springframework.modulith.ApplicationModule(
        displayName = "Data Engine BIZ",
        allowedDependencies = {
            "dataengine.api",
            "config.api",
            "auth.api",
            "approval.api",
            "shared"
        })
package com.jdec.platform.dataengine.biz;
