/** 审批引擎业务模块 (Approval BIZ) */
@org.springframework.modulith.ApplicationModule(
        displayName = "Approval BIZ",
        allowedDependencies = {"approval.api", "config.api", "auth.api", "hr.api", "shared"})
package com.jdec.platform.approval.biz;
