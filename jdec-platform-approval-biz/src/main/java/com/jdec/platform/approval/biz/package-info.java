/** 审批引擎业务模块 (Approval BIZ) */
@ApplicationModule(
        displayName = "Approval BIZ",
        allowedDependencies = {
            "approval.api",
            "common.api",
            "config.api",
            "config.open",
            "auth.api",
            "hr.api",
            "dataengine.api",
            "data.api",
            "validation",
            "shared"
        })
package com.jdec.platform.approval.biz;

import org.springframework.modulith.ApplicationModule;
