/**
 * Common 模块对外暴露的 API。
 *
 * <p>本模块只放<b>接口与 DTO</b>，不放实现，也不依赖任何 {@code *-biz} 模块。其他模块需要调用 Common 能力时，依赖本模块即可。
 *
 * <p><b>约定：</b>
 *
 * <ul>
 *   <li>DTO 按 {@code dto/request} + {@code dto/response} 分包（与 auth / config 等模块保持一致）。
 *   <li>接口默认返回纯 DTO；仅当需要「非 200 业务码 + 同时携带 data」时才返回 {@code ApiResponse}，并在 Javadoc 写明原因。
 *   <li>接口只暴露 Controller 真正调用的方法，内部使用的逻辑不要挂到接口上。
 * </ul>
 *
 * <p>声明为 {@link org.springframework.modulith.ApplicationModule.Type#OPEN}：本模块的包全部视为对外
 * API，其他模块可直接依赖。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Common API",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.jdec.platform.common.api;
