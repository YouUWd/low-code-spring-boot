package com.jdec.platform.config.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新模块请求 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateModuleReq {
    private Long id;
    private String name;
    private String description;

    public boolean hasChanges() {
        return name != null || description != null;
    }

    public void validate() {
        if (!hasChanges()) {
            throw new IllegalArgumentException("No changes provided");
        }
    }
}
