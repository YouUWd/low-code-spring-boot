package com.jdec.platform.dataengine.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "动态模块表数据插入一行数据")
public class DynamicInsertReq implements Serializable {

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块行数据")
    private Map<String, Object> row; // {"member.parentId":0}
}
