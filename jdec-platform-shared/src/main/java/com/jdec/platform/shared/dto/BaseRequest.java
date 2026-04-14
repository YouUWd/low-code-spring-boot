package com.jdec.platform.shared.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 基础请求对象
 *
 * <p>所有 Request 对象的基类，提供统一的验证和处理机制
 */
public abstract class BaseRequest {

    /**
     * 验证请求对象
     *
     * <p>子类应该重写此方法实现具体的验证逻辑
     *
     * @throws IllegalArgumentException 验证失败时抛出
     */
    public void validate() {
        // 子类实现具体验证逻辑
    }
}
