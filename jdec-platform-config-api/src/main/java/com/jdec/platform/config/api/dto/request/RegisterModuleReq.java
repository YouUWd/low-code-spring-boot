package com.jdec.platform.config.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 注册模块请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterModuleReq {
    private String code;
    private String name;
    private String description;

    public boolean isValid() {
        return isValidCode() && isValidName();
    }

    public void validate() {
        if (!isValid()) {
            throw new IllegalArgumentException("Invalid module request");
        }
    }

    private boolean isValidCode() {
        return code != null && !code.trim().isEmpty();
    }

    private boolean isValidName() {
        return name != null && !name.trim().isEmpty();
    }
}
