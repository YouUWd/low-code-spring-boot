package com.jdec.platform.dataengine.api.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.*;

@Data
@EqualsAndHashCode
@Schema(description = "模块变更对象")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModuleChangeDTO implements Serializable {

    private String name;

    private Long mId;

    private ActionChangeDTO actions;
}
